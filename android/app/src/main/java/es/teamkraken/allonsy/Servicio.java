package es.teamkraken.allonsy;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * El asistente: se queda en marcha en segundo plano (con una notificación fija),
 * da los avisos a su hora y habla por los auriculares.
 */
public class Servicio extends Service {
    static final String CANAL_FIJO = "allonsy_fijo", CANAL_AVISOS = "allonsy_avisos";
    static final int ID_FIJO = 1;
    static final String ACCION_TICK = "tick", ACCION_HABLAR = "hablar", ACCION_PARAR = "parar";

    private TextToSpeech tts;
    private boolean ttsListo = false;
    private final List<String> cola = new ArrayList<>();
    private Almacen almacen;
    private AudioManager audio;
    private AudioFocusRequest foco;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int avisoId = 100;

    public static void arrancar(Context c) {
        Intent i = new Intent(c, Servicio.class).setAction(ACCION_TICK);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void hablar(Context c, String texto) {
        Intent i = new Intent(c, Servicio.class).setAction(ACCION_HABLAR).putExtra("texto", texto);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void parar(Context c) {
        c.stopService(new Intent(c, Servicio.class));
        cancelarAlarma(c);
    }

    @Override public void onCreate() {
        super.onCreate();
        almacen = new Almacen(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        crearCanales();
        ponerEnPrimerPlano();
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                configurarVoz();
                ttsListo = true;
                for (String t : new ArrayList<>(cola)) decir(t);
                cola.clear();
            }
        });
        if (Build.VERSION.SDK_INT >= 23) audio.registerAudioDeviceCallback(callbackAuriculares, h);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ponerEnPrimerPlano();
        String accion = intent != null ? intent.getAction() : ACCION_TICK;
        if (ACCION_HABLAR.equals(accion)) {
            String t = intent.getStringExtra("texto");
            if (t != null) decir(t);
        } else {
            tick();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (Build.VERSION.SDK_INT >= 23) audio.unregisterAudioDeviceCallback(callbackAuriculares);
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ---------- Notificaciones ----------
    private void crearCanales() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel fijo = new NotificationChannel(CANAL_FIJO, "Asistente activo", NotificationManager.IMPORTANCE_MIN);
        fijo.setDescription("Aviso fijo mientras Allons-y te acompaña");
        fijo.setShowBadge(false);
        nm.createNotificationChannel(fijo);
        NotificationChannel av = new NotificationChannel(CANAL_AVISOS, "Avisos", NotificationManager.IMPORTANCE_HIGH);
        av.setDescription("Recordatorios de tus tareas");
        av.enableVibration(true);
        nm.createNotificationChannel(av);
    }
    private PendingIntent abrirApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void ponerEnPrimerPlano() {
        Notification n = new NotificationCompat.Builder(this, CANAL_FIJO)
                .setSmallIcon(R.drawable.ic_stat_allonsy)
                .setContentTitle(almacen.titulo() + " te acompaña")
                .setContentText("Te avisaré de lo que tienes que hacer")
                .setOngoing(true).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(abrirApp()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_FIJO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID_FIJO, n);
    }
    private void notificar(String texto, boolean conSonido) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CANAL_AVISOS)
                .setSmallIcon(R.drawable.ic_stat_allonsy)
                .setContentTitle(almacen.titulo())
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(abrirApp());
        if (!conSonido) b.setSilent(true);
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(avisoId++, b.build()); } catch (Exception ignored) {}
    }

    // ---------- Voz ----------
    private void configurarVoz() {
        Locale es = new Locale("es", "ES");
        tts.setLanguage(es);
        tts.setSpeechRate(0.95f);
        tts.setPitch(1.05f);
        try {
            Voice mejor = null; int puntos = -999;
            for (Voice v : tts.getVoices()) {
                if (v.getLocale() == null || !"es".equals(v.getLocale().getLanguage())) continue;
                int p = 0;
                if ("ES".equals(v.getLocale().getCountry())) p += 100;
                if (!v.isNetworkConnectionRequired()) p += 20;
                String n = v.getName().toLowerCase(Locale.ROOT);
                if (n.contains("female") || n.contains("eea") || n.contains("eec") || n.contains("eef")) p += 50;
                if (n.contains("male") && !n.contains("female")) p -= 60;
                if (n.contains("eed") || n.contains("eee")) p -= 40;
                if (p > puntos) { puntos = p; mejor = v; }
            }
            if (mejor != null) tts.setVoice(mejor);
        } catch (Exception ignored) {}
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { soltarFoco(); }
            @Override public void onError(String id) { soltarFoco(); }
        });
        if (Build.VERSION.SDK_INT >= 21) tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
    }
    private void decir(String texto) {
        if (!ttsListo) { cola.add(texto); return; }
        pedirFoco();
        Bundle p = new Bundle();
        tts.speak(texto, TextToSpeech.QUEUE_ADD, p, "a" + System.currentTimeMillis());
    }
    private void pedirFoco() {
        if (Build.VERSION.SDK_INT >= 26) {
            foco = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build()).build();
            audio.requestAudioFocus(foco);
        }
    }
    private void soltarFoco() { if (Build.VERSION.SDK_INT >= 26 && foco != null) audio.abandonAudioFocusRequest(foco); }

    /** ¿Hay auriculares conectados (Bluetooth, cable o USB)? */
    boolean hayAuriculares() {
        if (Build.VERSION.SDK_INT < 23) return audio.isWiredHeadsetOn() || audio.isBluetoothA2dpOn();
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) if (esAuricular(d)) return true;
        return false;
    }
    static boolean esAuricular(AudioDeviceInfo d) {
        int t = d.getType();
        if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET) return true;
        if (Build.VERSION.SDK_INT >= 26 && t == AudioDeviceInfo.TYPE_USB_HEADSET) return true;
        return Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER);
    }

    /** Da un aviso: habla si hay auriculares y siempre deja el mensaje escrito. */
    private void avisar(String hablado, String escrito) {
        boolean auris = hayAuriculares();
        almacen.anotarAviso(Textos.horaMinuto(), Textos.hoy(), escrito);
        if (auris || almacen.hablarSinAuriculares()) { decir(hablado); notificar(escrito, false); }
        else notificar(escrito, true);
    }

    // Al ponerse los auriculares, saluda con lo siguiente que toca
    private final AudioDeviceCallback callbackAuriculares = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            boolean auris = false;
            for (AudioDeviceInfo d : added) if (d.isSink() && esAuricular(d)) auris = true;
            if (!auris || !almacen.vozActiva() || !enHorario()) return;
            long ahora = System.currentTimeMillis();
            if (ahora - almacen.ultimoHola() < 10 * 60000L) return;
            almacen.ultimoHola(ahora);
            h.postDelayed(() -> decir(Textos.hola(almacen)), 2500);
        }
    };

    // ---------- Reloj de avisos ----------
    private boolean enHorario() {
        JSONObject s = almacen.ajustes();
        int n = Textos.minutosAhora(), a = Textos.aMin(s.optString("start", "08:00")), b = Textos.aMin(s.optString("end", "22:00"));
        if (a < 0 || b < 0) return true;
        return a <= b ? (n >= a && n <= b) : (n >= a || n <= b);
    }

    private void tick() {
        if (!almacen.vozActiva()) { programar(); return; }
        String hoy = Textos.hoy(); int n = Textos.minutosAhora(); long ahora = System.currentTimeMillis();
        JSONObject s = almacen.ajustes(); JSONArray t = almacen.tareas();
        int antes = Math.max(0, s.optInt("before", 10));
        for (int i = 0; i < t.length(); i++) {
            JSONObject x = t.optJSONObject(i);
            if (x == null || x.optBoolean("done") || !hoy.equals(x.optString("date")) || x.optString("time").isEmpty()) continue;
            int tm = Textos.aMin(x.optString("time")); String id = x.optString("id"), tx = x.optString("text");
            if (antes > 0 && n >= tm - antes && n < tm && !almacen.dicho(id + "_pre", hoy)) {
                almacen.marcar(id + "_pre", hoy);
                int f = tm - n; String min = f + (f == 1 ? " minuto" : " minutos");
                avisar("Recordatorio: dentro de " + min + ", " + tx + ".", "Dentro de " + min + " (" + x.optString("time") + "): " + tx);
            }
            if (n >= tm && n <= tm + 5 && !almacen.dicho(id + "_now", hoy)) {
                almacen.marcar(id + "_now", hoy); almacen.marcar(id + "_pre", hoy);
                avisar("Es la hora: " + tx + ".", "Es la hora: " + tx);
            }
        }
        String mo = s.optString("morning", "08:30");
        if (!mo.isEmpty() && n >= Textos.aMin(mo) && n <= Textos.aMin(mo) + 90 && !almacen.dicho("morning", hoy)) {
            almacen.marcar("morning", hoy); almacen.ultimoRepaso(ahora);
            int p = Textos.pendientesHoy(t, hoy).size();
            avisar(Textos.manana(almacen), "Buenos días. " + (p == 0 ? "Hoy no tienes nada apuntado." : "Hoy tienes " + (p == 1 ? "1 cosa." : p + " cosas.")));
        }
        String ni = s.optString("night", "21:30");
        if (!ni.isEmpty() && n >= Textos.aMin(ni) && n <= Textos.aMin(ni) + 60 && !almacen.dicho("night", hoy)) {
            almacen.marcar("night", hoy); almacen.ultimoRepaso(ahora);
            avisar(Textos.noche(this, almacen), "Tu resumen del día está listo. Míralo en «Conseguido».");
        }
        long cada = Math.max(5, s.optInt("interval", 60)) * 60000L;
        if (enHorario() && ahora - almacen.ultimoRepaso() >= cada) {
            almacen.ultimoRepaso(ahora);
            if (hayAuriculares() || almacen.hablarSinAuriculares()) decir(Textos.repaso(almacen));
        }
        programar();
    }

    /** Programa la siguiente comprobación: el próximo aviso o, como mucho, dentro de 15 minutos. */
    private void programar() {
        Calendar c = Calendar.getInstance(); c.set(Calendar.SECOND, 5); c.set(Calendar.MILLISECOND, 0);
        long base = c.getTimeInMillis(), ahora = System.currentTimeMillis();
        int n = Textos.minutosAhora(); String hoy = Textos.hoy();
        JSONObject s = almacen.ajustes(); JSONArray t = almacen.tareas();
        List<Integer> cand = new ArrayList<>();
        int antes = Math.max(0, s.optInt("before", 10));
        for (int i = 0; i < t.length(); i++) {
            JSONObject x = t.optJSONObject(i);
            if (x == null || x.optBoolean("done") || !hoy.equals(x.optString("date")) || x.optString("time").isEmpty()) continue;
            int tm = Textos.aMin(x.optString("time"));
            if (antes > 0) cand.add(tm - antes);
            cand.add(tm);
        }
        cand.add(Textos.aMin(s.optString("morning", "08:30")));
        cand.add(Textos.aMin(s.optString("night", "21:30")));
        long cada = Math.max(5, s.optInt("interval", 60)) * 60000L;
        long siguienteRepaso = almacen.ultimoRepaso() + cada;
        long objetivo = ahora + 15 * 60000L;
        if (siguienteRepaso > ahora && siguienteRepaso < objetivo) objetivo = siguienteRepaso;
        for (int m : cand) {
            if (m <= n) continue;
            long cuando = base + (m - n) * 60000L;
            if (cuando > ahora && cuando < objetivo) objetivo = cuando;
        }
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = alarma(this);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, objetivo, pi);
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, objetivo, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, objetivo, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, objetivo, pi);
        }
    }
    static PendingIntent alarma(Context c) {
        Intent i = new Intent(c, AlarmaReceiver.class);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static void cancelarAlarma(Context c) {
        ((AlarmManager) c.getSystemService(ALARM_SERVICE)).cancel(alarma(c));
    }
}
