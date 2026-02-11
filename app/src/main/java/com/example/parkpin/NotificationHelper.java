package com.example.parkpin;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;
import java.util.Calendar;

public class NotificationHelper {

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
}