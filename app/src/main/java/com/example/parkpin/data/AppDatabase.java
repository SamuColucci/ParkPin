package com.example.parkpin.data;

import android.content.Context;

import androidx.room.Database;
import com.example.parkpin.model.PosizioneAutoEntity;

import androidx.room.Room;
import androidx.room.RoomDatabase;

//Classe per la gestione del database e la sua creazione
@Database(entities = {PosizioneAutoEntity.class}, version =1)
public abstract class AppDatabase extends RoomDatabase{

    //Creazione e gestione del Dao da parte di Room, astrazione di SQLLite
    public abstract PosizioneAutoDao posizioneAutoDao();

    //Istanza per capire se il database è univoco
    private static volatile AppDatabase INSTANCE;
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Creazione database fisico "parkpin_db"
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "parkpin_db")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}
