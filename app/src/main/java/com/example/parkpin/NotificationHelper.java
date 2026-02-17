package com.example.parkpin;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;
import java.util.Calendar;

public class NotificationHelper {

    private static long lastNotificationTime = 0;

    /**
     * Metodo unico per programmare l'avviso di scadenza
     */
    public static void prenotaAvviso(Context context, int oraScadenza, int minutiScadenza) {
        Calendar calendarScadenza = Calendar.getInstance();
        calendarScadenza.set(Calendar.HOUR_OF_DAY, oraScadenza);
        calendarScadenza.set(Calendar.MINUTE, minutiScadenza);
        calendarScadenza.set(Calendar.SECOND, 0);

        // Se l'orario è già passato, s'intende domani
        if (calendarScadenza.before(Calendar.getInstance())) {
            calendarScadenza.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Notifica 10 minuti prima
        long tempoNotifica = calendarScadenza.getTimeInMillis() - (10 * 60 * 1000);

        // Se mancano meno di 10 minuti, avvisiamo tra 30 secondi
        if (tempoNotifica <= System.currentTimeMillis()) {
            tempoNotifica = System.currentTimeMillis() + (30 * 1000);
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ParkingAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (am != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoNotifica, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoNotifica, pi);
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoNotifica, pi);
            }
        }

        String orario = String.format("%02d:%02d", oraScadenza, minutiScadenza);
        Toast.makeText(context, "Avviso impostato per le " + orario, Toast.LENGTH_SHORT).show();


        context.getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE)
                .edit()
                .putString("orario_scadenza_timer", orario)
                .apply();
    }
    public static void inviaNotificaArrivoImmediata(Context context) {

        if (System.currentTimeMillis() - lastNotificationTime < 30000) {
            return;
        }

        lastNotificationTime = System.currentTimeMillis();
        String channelId = "arrival_notification_channel";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Creazione del Canale (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Notifica Arrivo",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avviso quando sei vicino all'auto");
            channel.enableVibration(true);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        // Costruzione della Notifica
        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.baseline_directions_car_24)
                        .setContentTitle("ParkPin")
                        .setContentText("Sei arrivato alla tua auto! 🚗")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setVibrate(new long[]{0, 500, 200, 500}); // Vibrazione personalizzata

        if (notificationManager != null) {
            notificationManager.notify(2002, builder.build());
        }

    }
    public static void schedulaProssimoControlloPosizione(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, LocationCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long tempoProssimoControllo = System.currentTimeMillis() + (60 * 1000);

        if (am != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // CONTROLLO CRUCIALE: Se non abbiamo il permesso, usiamo un allarme normale
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoProssimoControllo, pi);
                } else {
                    // Fallback: Allarme non esatto (non crasha)
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoProssimoControllo, pi);

                    // Opzionale: Chiedi all'utente di abilitare il permesso nelle impostazioni
                    // Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    // context.startActivity(i);
                }
            } else {
                // Sotto Android 12 funzionava senza permessi speciali
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoProssimoControllo, pi);
            }
        }
    }
}