package es.teamkraken.allonsy;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Puente entre la app (web) y el asistente nativo. */
@CapacitorPlugin(name = "Asistente")
public class AsistentePlugin extends Plugin {

    @PluginMethod public void setState(PluginCall call) {
        String json = call.getString("state", "{}");
        Almacen a = new Almacen(getContext());
        a.guardarEstado(json);
        if (a.vozActiva()) { try { Servicio.arrancar(getContext()); } catch (Exception ignored) {} }
        else Servicio.parar(getContext());
        call.resolve();
    }

    @PluginMethod public void start(PluginCall call) {
        pedirNotificaciones();
        try { Servicio.arrancar(getContext()); } catch (Exception ignored) {}
        call.resolve();
    }

    @PluginMethod public void stop(PluginCall call) { Servicio.parar(getContext()); call.resolve(); }

    @PluginMethod public void speak(PluginCall call) {
        String t = call.getString("text", "");
        if (!t.isEmpty()) { try { Servicio.hablar(getContext(), t); } catch (Exception ignored) {} }
        call.resolve();
    }

    @PluginMethod public void takeAlerts(PluginCall call) {
        JSObject r = new JSObject(); r.put("alerts", new Almacen(getContext()).sacarAvisos()); call.resolve(r);
    }

    @PluginMethod public void status(PluginCall call) {
        Context c = getContext(); JSObject r = new JSObject();
        r.put("notifications", Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        r.put("battery", Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        r.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        call.resolve(r);
    }

    @PluginMethod public void askNotifications(PluginCall call) { pedirNotificaciones(); call.resolve(); }

    @PluginMethod public void askBattery(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(i);
            } catch (Exception e) {
                try { getActivity().startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
            }
        }
        call.resolve();
    }

    @PluginMethod public void openAppSettings(PluginCall call) {
        try {
            getActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
        } catch (Exception ignored) {}
        call.resolve();
    }

    private void pedirNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }
}
