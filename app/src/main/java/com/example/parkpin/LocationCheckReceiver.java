package com.example.parkpin;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

public class LocationCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("navigazione_attiva", false)) return;

        float destLat = prefs.getFloat("dest_lat", 0);
        float destLon = prefs.getFloat("dest_lon", 0);

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            // Proviamo a prendere la posizione GPS (più precisa)
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            // Se la posizione GPS è vecchia o nulla, proviamo quella di rete
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (loc != null) {
                // CONTROLLO PRECISIONE: Se l'errore è > 50 metri, la posizione non è affidabile
                if (loc.getAccuracy() > 50) {
                    Log.d("PARKPIN_ALARM", "Posizione poco precisa (" + loc.getAccuracy() + "m). Riprovo...");
                    // Non mandiamo la notifica, ma programmiamo un nuovo controllo ravvicinato
                    NotificationHelper.schedulaProssimoControlloPosizione(context);
                    return;
                }

                GeoPoint current = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                GeoPoint dest = new GeoPoint(destLat, destLon);
                double distance = current.distanceToAsDouble(dest);

                Log.d("PARKPIN_ALARM", "Distanza precisa: " + (int)distance + "m (Acc: " + loc.getAccuracy() + "m)");

                if (distance < 50) { // SOGLIA ARRIVO (leggermente alzata per compensare il background)
                    NotificationHelper.inviaNotificaArrivoImmediata(context);
                    prefs.edit().putBoolean("navigazione_attiva", false).apply();
                } else {
                    NotificationHelper.schedulaProssimoControlloPosizione(context);
                }
            } else {
                // Nessuna posizione trovata, riprova al prossimo ciclo
                NotificationHelper.schedulaProssimoControlloPosizione(context);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}