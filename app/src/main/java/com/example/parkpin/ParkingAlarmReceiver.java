package com.example.parkpin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ParkingAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "parking_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Creazione Canale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Scadenza Parcheggio",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifiche per la scadenza del timer");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Convertiamo il logo colorato in Bitmap per la LargeIcon
        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_splash_logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                // 1. SMALL ICON: Usa un vettoriale trasparente (es. l'auto o una 'P')
                // NON usare ic_splash_logo qui!
                .setSmallIcon(R.drawable.baseline_directions_car_24)

                // 2. COLOR: Colora la small icon del tuo Giallo ParkPin
                .setColor(Color.parseColor("#FFC107"))

                // 3. LARGE ICON: Qui puoi mettere il logo colorato completo
                .setLargeIcon(largeIcon)

                .setContentTitle("⚠️ Scadenza Parcheggio!")
                .setContentText("Il tempo sta per scadere. Torna all'auto!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(101, builder.build());
    }
}