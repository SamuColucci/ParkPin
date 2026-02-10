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
    @Override
    public void onReceive(Context context, Intent intent) {

        // 1. Definisci l'ID del canale
        String channelId = "parkpin_timer";

        // 2. Ottieni il Manager
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 3. RICREA IL CANALE (Fondamentale su Android 8+)
        // Se esiste già non fa nulla, se non esiste lo crea. È una sicurezza.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Scadenza Parcheggio",
                    NotificationManager.IMPORTANCE_HIGH // Importanza ALTA per farlo suonare
            );
            channel.setDescription("Notifiche timer parcheggio");
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        // 4. Intent per aprire l'app quando clicchi
        Intent tapIntent = new Intent(context, HomeFragment.class); // Apre la Welcome o la Main
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);

        // 5. Costruisci la notifica
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Icona di sistema sicura
                .setContentTitle("⚠️ SCADE IL PARCHEGGIO!")
                .setContentText("Mancano 10 minuti. Corri!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // 6. SPARA LA NOTIFICA
        notificationManager.notify(1001, builder.build());
    }
}