package com.example.parkpin.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/*
 * Codice che gestisce la entity necessaria alla gestione della posizione della macchina
 * Presenta come attributi un id che ne identifica la chiave primaria,
 * una posizione nello spazio, un orario di scadenza opzionale e una nota opzionale
 * */
@Entity(tableName = "posizione_auto")
public class PosizioneAutoEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public double longitudine;
    public double latitudine;
    public boolean isPagamento;
    public long orarioArrivo;
    @Nullable
    public Long orarioScadenza;

    @Nullable
    public String note;
    @Nullable
    public String nomeLuogo;

    public PosizioneAutoEntity(){
    }

    public PosizioneAutoEntity(double latitudine, double longitudine,
                               @Nullable String nomeLuogo, boolean isPagamento,
                               @Nullable String note, long orarioArrivo,
                               @Nullable Long orarioScadenza) {
        this.latitudine = latitudine;
        this.longitudine = longitudine;
        this.nomeLuogo = nomeLuogo;
        this.isPagamento = isPagamento;
        this.note = note;
        this.orarioArrivo = orarioArrivo;
        this.orarioScadenza = orarioScadenza;
    }
}