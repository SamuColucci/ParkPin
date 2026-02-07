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

import java.util.ArrayList;


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
}