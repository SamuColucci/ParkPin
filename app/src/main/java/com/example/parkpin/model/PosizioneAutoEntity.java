package com.example.parkpin.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

//Idea 1 senza login -> legato al telefono, non portabile fra vari dispositivi
//Idea 2 aggiunta preferiti e gestione login -> portabile, ma gestione sessione e controlli accessi e password(Aggiunta e recupero)

/*
 * Codice che gestisce la entity necessaria alla gestione della posizione della macchina
 * Presenta come attributi un id che ne identifica la chiave primaria,
 * una poszione nello spazio, un orario di scadenza opzionale e una nota opzionale
 * */
@Entity(tableName = "posizione_auto")
public class PosizioneAutoEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public double longitudine;
    public double latitudine;

    // Info inserita dall'utente o derivata dal tipo di parcheggio
    // True = Strisce Blu/Garage, False = Strisce Bianche
    public boolean isPagamento;

    // Timestamp dell'arrivo (per calcolare da quanto tempo sei lÃ¬)
    public long orarioArrivo;
    //Long per salavare i millisecondi timestamp e poter restituire null usiamo wrapper class Long
    @Nullable
    public Long orarioScadenza;

    //Informazioni addizionali per la gestione di una lista di parcheggi preferiti,
    //Titolo per i preferiti (es. "Casa", "Lavoro")
    @Nullable
    public String note;

    // Info generate automaticamente da Nominatim (Reverse Geocoding)
    // Es. "Parcheggio Piazza Dante, Roma"
    @Nullable
    public String nomeLuogo;
    //Gestione parcheggi in base alla login
    //public String userId;

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