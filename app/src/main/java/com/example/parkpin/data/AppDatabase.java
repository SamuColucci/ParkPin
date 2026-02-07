package com.example.parkpin.data;

import android.content.Context;

import androidx.room.Database;
import com.example.parkpin.model.PosizioneAutoEntity;

import androidx.room.Room;
import androidx.room.RoomDatabase;


@Database(entities = {PosizioneAutoEntity.class}, version =1)
public abstract class AppDatabase extends RoomDatabase{

    //Creazione e gestione del Dao da parte di Room
    public abstract PosizioneAutoDao posizioneAutoDao();

    //Istanza per capire se il database è univoco
    private static volatile AppDatabase INSTANCE;
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Qui Room crea il database fisico "parkpin_db"
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "parkpin_db")
                            .allowMainThreadQueries()//Ci permette di fare query senza bloccare tutto
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}
