package com.example.parkpin.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.parkpin.model.PosizioneAutoEntity;

import java.util.List;

@Dao
public interface PosizioneAutoDao {

    @Insert
    void insert(PosizioneAutoEntity auto);

    // 2. Cancella la vecchia posizione (per la logica "Solo 1 auto")
    @Query("DELETE FROM posizione_auto")
    void svuotaTutto();

    // 3. Leggi dove è l'auto (ritorna null se non c'è)
    @Query("SELECT * FROM posizione_auto LIMIT 1")
    PosizioneAutoEntity getAuto();
}
