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

}
