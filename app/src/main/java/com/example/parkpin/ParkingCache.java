package com.example.parkpin;

import com.example.parkpin.data.OverpassResponse;
import org.osmdroid.util.GeoPoint; // Import fondamentale
import java.util.List;

public class ParkingCache {
    // 1. La lista dei parcheggi (i dati veri e propri)
    public static List<OverpassResponse.Elemento> parcheggiSalvati = null;

    // 2. La posizione dove li abbiamo scaricati (per calcolare se siamo ancora vicini)
    // QUESTA VARIABILE MANCAVA ed è fondamentale per la logica "intelligente"
    public static GeoPoint posizioneSalvataggio = null;
}