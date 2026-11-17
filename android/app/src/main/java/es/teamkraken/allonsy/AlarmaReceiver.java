package es.teamkraken.allonsy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Despierta al asistente a la hora de cada aviso. */
public class AlarmaReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (new Almacen(c).vozActiva()) {
            try { Servicio.arrancar(c); } catch (Exception ignored) {}
        }
    }
}
