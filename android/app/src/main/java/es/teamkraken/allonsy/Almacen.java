package es.teamkraken.allonsy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Guarda en el móvil la agenda que manda la app y el registro de avisos ya dados. */
public class Almacen {
    private final SharedPreferences sp;

    public Almacen(Context c) { sp = c.getSharedPreferences("allonsy", Context.MODE_PRIVATE); }

    public JSONObject estado() {
        try { return new JSONObject(sp.getString("estado", "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    public void guardarEstado(String json) { sp.edit().putString("estado", json).apply(); }

    public JSONObject ajustes() {
        JSONObject s = estado().optJSONObject("settings");
        return s != null ? s : new JSONObject();
    }
    public JSONArray tareas() {
        JSONArray t = estado().optJSONArray("tasks");
        return t != null ? t : new JSONArray();
    }
    public String nombre() { return estado().optString("user", "").trim(); }
    public String titulo() { String t = estado().optString("title", "").trim(); return t.isEmpty() ? "Allons-y" : t; }

    public boolean vozActiva() { return ajustes().optBoolean("voiceOn", true); }
    public boolean hablarSinAuriculares() { return ajustes().optBoolean("speakerToo", false); }

    // Registro de avisos dados hoy (clave -> fecha)
    public boolean dicho(String clave, String hoy) {
        try { return hoy.equals(new JSONObject(sp.getString("dicho", "{}")).optString(clave)); } catch (Exception e) { return false; }
    }
    public void marcar(String clave, String hoy) {
        try {
            JSONObject o = new JSONObject(sp.getString("dicho", "{}"));
            JSONObject n = new JSONObject();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); if (hoy.equals(o.optString(k))) n.put(k, o.optString(k)); }
            n.put(clave, hoy);
            sp.edit().putString("dicho", n.toString()).apply();
        } catch (Exception ignored) {}
    }

    public long ultimoRepaso() { return sp.getLong("ultimoRepaso", 0); }
    public void ultimoRepaso(long t) { sp.edit().putLong("ultimoRepaso", t).apply(); }
    public long ultimoHola() { return sp.getLong("ultimoHola", 0); }
    public void ultimoHola(long t) { sp.edit().putLong("ultimoHola", t).apply(); }

    /** Avisos escritos pendientes de mostrar en la app. */
    public void anotarAviso(String hora, String fecha, String texto) {
        try {
            JSONArray a = new JSONArray(sp.getString("avisos", "[]"));
            JSONObject o = new JSONObject(); o.put("t", hora); o.put("d", fecha); o.put("text", texto);
            a.put(o);
            while (a.length() > 30) a.remove(0);
            sp.edit().putString("avisos", a.toString()).apply();
        } catch (Exception ignored) {}
    }
    public String sacarAvisos() {
        String s = sp.getString("avisos", "[]");
        sp.edit().putString("avisos", "[]").apply();
        return s;
    }
}
