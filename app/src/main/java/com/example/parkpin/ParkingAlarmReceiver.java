package com.example.parkpin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ParkingAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "parking_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Creazione Canale (obbligatorio da Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Scadenza Parcheggio",
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        // Cosa succede al click sulla notifica
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_directions_car_24)
                .setContentTitle("⚠️ Scadenza Parcheggio!")
                .setContentText("Il tempo sta per scadere. Torna all'auto!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(101, builder.build());
    }
}