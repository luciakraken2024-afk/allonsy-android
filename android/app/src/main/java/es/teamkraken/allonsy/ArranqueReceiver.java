package es.teamkraken.allonsy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Al encender el móvil, el asistente vuelve a ponerse en marcha solo. */
public class ArranqueReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.MY_PACKAGE_REPLACED".equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        if (new Almacen(c).vozActiva()) {
            try { Servicio.arrancar(c); } catch (Exception ignored) {}
        }
    }
}
