package es.teamkraken.allonsy;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Lo que dice la voz. Mismo texto que la web y que los Atajos. */
public class Textos {
    static final Locale ES = new Locale("es", "ES");

    static String hoy() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime()); }
    static String horaMinuto() { return new SimpleDateFormat("HH:mm", Locale.US).format(Calendar.getInstance().getTime()); }
    static int minutosAhora() { Calendar c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE); }
    static int aMin(String hm) {
        try { String[] p = hm.split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); } catch (Exception e) { return -1; }
    }
    static String hora(String hm) {
        String[] p = hm.split(":"); int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        if (m == 0) return "las " + h;
        if (m == 30) return "las " + h + " y media";
        if (m == 15) return "las " + h + " y cuarto";
        return "las " + h + " y " + m;
    }
    static String fechaLarga(String d) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return new SimpleDateFormat("EEEE, d 'de' MMMM", ES).format(in.parse(d));
        } catch (Exception e) { return d; }
    }
    static int diasFinMes() {
        Calendar c = Calendar.getInstance();
        return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH);
    }
    static String diasFinMesTexto() {
        int n = diasFinMes();
        return n == 0 ? "hoy es el último día del mes" : n == 1 ? "queda 1 día para acabar el mes" : "quedan " + n + " días para acabar el mes";
    }
    static String unir(List<String> l) { return android.text.TextUtils.join(". ", l); }
    static String mayus(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1); }

    static List<JSONObject> pendientesHoy(JSONArray t, String hoy) {
        List<JSONObject> r = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x != null && !x.optBoolean("done") && hoy.equals(x.optString("date"))) r.add(x); }
        Collections.sort(r, (a, b) -> (a.optString("time").isEmpty() ? "99:99" : a.optString("time")).compareTo(b.optString("time").isEmpty() ? "99:99" : b.optString("time")));
        return r;
    }
    static List<JSONObject> delMes(JSONArray t, String hoy) {
        String mes = hoy.substring(0, 7); List<JSONObject> r = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x != null && !x.optBoolean("done") && !x.optString("month").isEmpty() && x.optString("month").compareTo(mes) <= 0) r.add(x); }
        return r;
    }
    static List<JSONObject> hechasHoy(JSONArray t, String hoy) {
        List<JSONObject> r = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) { JSONObject x = t.optJSONObject(i); if (x == null || !x.optBoolean("done")) continue;
            String d = x.optString("doneDate", x.optString("date")); if (hoy.equals(d)) r.add(x); }
        return r;
    }
    static List<String> nombres(List<JSONObject> l) { List<String> r = new ArrayList<>(); for (JSONObject x : l) r.add(x.optString("text")); return r; }

    static String item(JSONObject x, int ahora) {
        String t = x.optString("time"), tx = x.optString("text");
        if (t.isEmpty()) return tx;
        if (aMin(t) < ahora) return "se te ha pasado la hora de " + (tx.isEmpty() ? tx : tx.substring(0, 1).toLowerCase(ES) + tx.substring(1));
        return "a " + hora(t) + ", " + tx;
    }
    static String parteMes(List<JSONObject> pm, boolean siempre) {
        if (pm.isEmpty() || (!siempre && diasFinMes() > 5)) return "";
        return " Este mes te " + (pm.size() == 1 ? "queda una cosa" : "quedan " + pm.size() + " cosas") + ": " + unir(nombres(pm)) + ". " + mayus(diasFinMesTexto()) + ".";
    }

    public static String manana(Almacen a) {
        String hoy = hoy(), nm = a.nombre(); int ahora = minutosAhora(); JSONArray t = a.tareas();
        List<JSONObject> p = pendientesHoy(t, hoy);
        StringBuilder s = new StringBuilder("Buenos días" + (nm.isEmpty() ? "" : ", " + nm) + ". Hoy es " + fechaLarga(hoy) + ". ");
        if (p.isEmpty()) s.append("No tienes nada apuntado para hoy.");
        else { List<String> it = new ArrayList<>(); for (JSONObject x : p) it.add(item(x, ahora));
            s.append(p.size() == 1 ? "Tienes una cosa para hoy: " : "Tienes " + p.size() + " cosas para hoy: ").append(unir(it)).append("."); }
        s.append(parteMes(delMes(t, hoy), true));
        return s + " ¡Allons-y!";
    }
    public static String repaso(Almacen a) {
        String hoy = hoy(), nm = a.nombre(); int ahora = minutosAhora(); JSONArray t = a.tareas();
        List<JSONObject> p = pendientesHoy(t, hoy);
        StringBuilder s = new StringBuilder((nm.isEmpty() ? "Son " : nm + ", son ") + hora(horaMinuto()) + ". ");
        if (p.isEmpty()) s.append("No tienes nada pendiente para hoy.");
        else { List<String> it = new ArrayList<>(); for (JSONObject x : p) it.add(item(x, ahora));
            s.append(p.size() == 1 ? "Te queda una cosa: " : "Te quedan " + p.size() + " cosas: ").append(unir(it)).append("."); }
        return s + parteMes(delMes(t, hoy), false);
    }
    public static String hola(Almacen a) {
        String hoy = hoy(), nm = a.nombre(); int ahora = minutosAhora(); JSONArray t = a.tareas();
        List<JSONObject> p = pendientesHoy(t, hoy);
        StringBuilder s = new StringBuilder("Hola" + (nm.isEmpty() ? "" : ", " + nm) + ". Soy " + a.titulo() + ". ");
        JSONObject sig = null; for (JSONObject x : p) if (!x.optString("time").isEmpty() && aMin(x.optString("time")) >= ahora) { sig = x; break; }
        if (p.isEmpty()) s.append("Hoy no te queda nada pendiente.");
        else if (sig != null) s.append(p.size() == 1 ? "Te queda una cosa hoy" : "Te quedan " + p.size() + " cosas hoy").append(". Lo siguiente, a ").append(hora(sig.optString("time"))).append(": ").append(sig.optString("text")).append(".");
        else s.append(p.size() == 1 ? "Te queda una cosa hoy: " : "Te quedan " + p.size() + " cosas hoy: ").append(unir(nombres(p))).append(".");
        return s + parteMes(delMes(t, hoy), false);
    }
    public static String noche(Context c, Almacen a) {
        String hoy = hoy(), nm = a.nombre(); JSONArray t = a.tareas();
        List<JSONObject> h = hechasHoy(t, hoy), p = pendientesHoy(t, hoy);
        StringBuilder s = new StringBuilder("Resumen del día" + (nm.isEmpty() ? "" : ", " + nm) + ". ");
        s.append(h.isEmpty() ? "Hoy no has marcado nada como hecho. " : "Hoy has conseguido " + (h.size() == 1 ? "una cosa: " : h.size() + " cosas: ") + unir(nombres(h)) + ". ");
        if (!p.isEmpty()) s.append("Ha quedado pendiente: ").append(unir(nombres(p))).append(". ");
        else if (!h.isEmpty()) s.append("No ha quedado nada pendiente. ¡Buen trabajo! ");
        String[] q = frase(c, hoy);
        if (q != null) s.append("Y para terminar el día, una frase de ").append(q[1]).append(": ").append(q[0]);
        return s.toString();
    }
    /** Frase del día: misma que en la web (rota por fecha desde el 1 de enero de 2026). */
    static String[] frase(Context c, String hoy) {
        try {
            InputStream in = c.getAssets().open("frases.json");
            byte[] b = new byte[in.available()]; in.read(b); in.close();
            JSONArray q = new JSONArray(new String(b, "UTF-8"));
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long dias = (f.parse(hoy).getTime() - f.parse("2026-01-01").getTime()) / 86400000L;
            int i = (int) (((dias % q.length()) + q.length()) % q.length());
            JSONObject o = q.getJSONObject(i);
            return new String[]{o.optString("t"), o.optString("a")};
        } catch (Exception e) { return null; }
    }
}
