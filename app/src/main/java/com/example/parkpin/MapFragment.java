package com.example.parkpin;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import android.os.StrictMode; // Serve per evitare blocchi su vecchi Android

import com.example.parkpin.data.OverpassResponse;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Response;


public class MapFragment extends Fragment {

    private MapView map = null;
    private MyLocationNewOverlay myLocationOverlay; // Il "Pallino Blu"

    // Gestore moderno dei Permessi
    // Appena l'utente risponde "Sì" o "No", questo codice viene eseguito
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    attivaPosizioneUtente(); // Ha detto sì! Accendi il GPS
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Carica configurazione Osmdroid
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Carica il layout (quello col FrameLayout che abbiamo sistemato)
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // =================================================================
        // 1. INIZIALIZZAZIONE MAPPA
        // =================================================================
        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        // =================================================================
        // 2. BOTTONE "GOOGLE MAPS" (Navigazione Esterna)
        // =================================================================
        android.widget.Button btnGoogle = view.findViewById(R.id.btn_google_maps);

        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                // Coordinate precise del Colosseo (Lat, Lon)
                android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=41.8902,12.4922");

                android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps"); // Forza Google Maps

                try {
                    startActivity(mapIntent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "Google Maps non trovato!", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        // =================================================================
        // 3. BOTTONE "LINEA BLU" (Navigazione Interna)
        // =================================================================
        android.widget.Button btnInterno = view.findViewById(R.id.btn_percorso_interno);

        if (btnInterno != null) {
            btnInterno.setOnClickListener(v -> {
                // Controlliamo se il GPS ha agganciato la posizione
                if (myLocationOverlay == null || myLocationOverlay.getMyLocation() == null) {
                    android.widget.Toast.makeText(requireContext(), "Attendi segnale GPS...", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                // Punti di Partenza (Tu) e Arrivo (Roma)
                org.osmdroid.util.GeoPoint start = myLocationOverlay.getMyLocation();
                org.osmdroid.util.GeoPoint end = new org.osmdroid.util.GeoPoint(41.035679, 14.518649);

                // Chiama la funzione che disegna la linea (quella col Thread)
                disegnaPercorsoSullaMappa(start, end);
            });
        }

        // =================================================================
        // BOTTONE 3: TROVA PARCHEGGI (Overpass API)
        // =================================================================
        android.widget.Button btnParcheggi = view.findViewById(R.id.btn_cerca_parcheggi);
        if (btnParcheggi != null) {
            btnParcheggi.setOnClickListener(v -> {
                // 1. Controllo GPS
                if (myLocationOverlay == null || myLocationOverlay.getMyLocation() == null) {
                    android.widget.Toast.makeText(requireContext(), "Attendi GPS...", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                // 2. Coordinate attuali
                double lat = myLocationOverlay.getMyLocation().getLatitude();
                double lon = myLocationOverlay.getMyLocation().getLongitude();

                // 3. Chiama la funzione di ricerca
                cercaParcheggiVicini(lat, lon);
            });
        }

        // =================================================================
        // 4. BOTTONE SALVA (FAB)
        // =================================================================
        com.google.android.material.floatingactionbutton.FloatingActionButton fab = view.findViewById(R.id.fab_salva);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                android.widget.Toast.makeText(requireContext(), "Funzione Salva in arrivo...", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        // =================================================================
        // 5. GESTIONE PERMESSI GPS
        // =================================================================
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Permesso già dato -> Accendi pallino blu
            attivaPosizioneUtente();
        } else {
            // Permesso mancante -> Chiedilo
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }




    // Questa funzione accende il pallino blu
    private void attivaPosizioneUtente() {
        // Crea il layer che mostra la posizione
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);

        myLocationOverlay.enableMyLocation(); // Attiva il sensore GPS
        myLocationOverlay.enableFollowLocation(); // La mappa segue l'utente se si muove

        // Aggiunge il layer sopra la mappa
        map.getOverlays().add(myLocationOverlay);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation(); // Risparmia batteria
    }


    // Funzione che calcola la strada usando OSRM (Open Source Routing Machine)
    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        android.widget.Toast.makeText(requireContext(), "Calcolo percorso...", android.widget.Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 1. Setup del RoadManager (usa i server gratuiti di OSRM)
                RoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");

                // 2. Definisci i punti (Partenza -> Arrivo)
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                // 3. Ottieni la strada (Richiesta Internet)
                Road road = roadManager.getRoad(waypoints);

                // 4. Se la strada è valida, disegnala
                if (road.mStatus == Road.STATUS_OK) {
                    // Crea la linea colorata
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(android.graphics.Color.BLUE); // Colore Blu
                    roadOverlay.getOutlinePaint().setStrokeWidth(15.0f); // Spessore

                    // Torna al Thread principale per aggiornare la grafica
                    requireActivity().runOnUiThread(() -> {
                        map.getOverlays().add(roadOverlay); // Aggiungi la linea
                        map.invalidate(); // Ridisegna

                        // Zoomma per far vedere tutto il viaggio
                        map.zoomToBoundingBox(road.mBoundingBox, true);
                    });
                } else {
                    requireActivity().runOnUiThread(() ->
                            android.widget.Toast.makeText(requireContext(), "Errore percorso!", android.widget.Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }



    private void cercaParcheggiVicini(double lat, double lon) {
        android.widget.Toast.makeText(requireContext(), "Cerco parcheggi...", android.widget.Toast.LENGTH_SHORT).show();

        // 1. Creiamo un Retrofit "volante" specifico per Overpass (perché l'URL è diverso da Nominatim)
        retrofit2.Retrofit retrofitOverpass = new retrofit2.Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/") // URL Base di Overpass
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        OverpassService service = retrofitOverpass.create(OverpassService.class);

        // 2. Costruiamo la query nel linguaggio Overpass QL
        // [out:json];node["amenity"="parking"](around:5000, lat, lon);out;
        // 5000 = raggio in metri (5km). Metti 10000 per 10km.
        String query = "[out:json];node[\"amenity\"=\"parking\"](around:1000," + lat + "," + lon + ");out;";
        // 3. Chiamata di rete (Nota come uso .data.OverpassResponse)
        service.cercaParcheggi(query).enqueue(new retrofit2.Callback<com.example.parkpin.data.OverpassResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.parkpin.data.OverpassResponse> call, retrofit2.Response<com.example.parkpin.data.OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Controlliamo se la lista esiste
                    if (response.body().elementi != null) {
                        mostraListaParcheggi(response.body().elementi);
                    } else {
                        android.widget.Toast.makeText(requireContext(), "Nessun parcheggio trovato.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    android.widget.Toast.makeText(requireContext(), "Errore server.", android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.parkpin.data.OverpassResponse> call, Throwable t) {
                android.widget.Toast.makeText(requireContext(), "Errore connessione!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Funzione per mostrare la lista a schermo (Dialog)
    private void mostraListaParcheggi(java.util.List<com.example.parkpin.data.OverpassResponse.Elemento> lista) {
        if (lista.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "Nessun parcheggio in zona.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Creiamo due array: uno per i nomi (da mostrare) e uno per gli oggetti veri
        String[] nomiParcheggi = new String[lista.size()];

        for (int i = 0; i < lista.size(); i++) {
            com.example.parkpin.data.OverpassResponse.Elemento p = lista.get(i);
            // Se ha un nome usalo, altrimenti scrivi "Parcheggio senza nome"
            String nome = (p.tags != null && p.tags.nome != null) ? p.tags.nome : "Parcheggio pubblico (" + (i+1) + ")";
            nomiParcheggi[i] = nome;
        }

        // Costruiamo il Dialogo
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Parcheggi Vicini (5km)")
                .setItems(nomiParcheggi, (dialog, which) -> {
                    // L'utente ha cliccato sull'elemento numero 'which'
                    com.example.parkpin.data.OverpassResponse.Elemento selezionato = lista.get(which);

                    // 1. Sposta la mappa lì
                    org.osmdroid.util.GeoPoint punto = new org.osmdroid.util.GeoPoint(selezionato.lat, selezionato.lon);
                    map.getController().animateTo(punto);
                    map.getController().setZoom(18.0);

                    // 2. Metti un marker
                    org.osmdroid.views.overlay.Marker m = new org.osmdroid.views.overlay.Marker(map);
                    m.setPosition(punto);
                    m.setTitle("Parcheggio");
                    m.setSnippet(nomiParcheggi[which]);
                    m.setIcon(androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_myplaces)); // Icona standard

                    map.getOverlays().add(m);
                    map.invalidate(); // Ridisegna

                    android.widget.Toast.makeText(requireContext(), "Selezionato: " + nomiParcheggi[which], android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }
}