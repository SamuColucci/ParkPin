package com.example.parkpin;

import com.example.parkpin.data.OverpassResponse;
import org.osmdroid.util.GeoPoint; // Import fondamentale
import java.util.List;

// Utilizziamo una cache dei parcheggi per risolvere il problema
// legato al servizio non sempre raggiungibile per la ricerca dei parcheggi
public class ParkingCache {
    public static List<OverpassResponse.Elemento> parcheggiSalvati = null;
    // Variabile per la gestione della cache che permette di modificarla
    // in caso di un allontanamento dall'ultima posizione salvata in cache
    public static GeoPoint posizioneSalvataggio = null;
}