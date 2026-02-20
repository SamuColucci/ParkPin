package com.example.parkpin.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.parkpin.model.PosizioneAutoEntity;

import java.util.List;

//Classe DAO per la posizione relativa all'auto
@Dao
public interface PosizioneAutoDao {

    @Insert
    void insert(PosizioneAutoEntity auto);

    @Query("DELETE FROM posizione_auto")
    void svuotaTutto();

    @Query("SELECT * FROM posizione_auto LIMIT 1")
    PosizioneAutoEntity getAuto();
}
