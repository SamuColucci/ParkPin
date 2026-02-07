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
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;

// Import per la ricerca
import com.example.parkpin.data.OverpassResponse;
import retrofit2.Call;
import retrofit2.Response;


public class MapFragment extends Fragment {

    private MapView map = null;
    private MyLocationNewOverlay myLocationOverlay; // Il "Pallino Blu"
    private Polyline currentRoute = null; // Variabile per ricordare la linea blu attuale

    private android.widget.Button btnStopNav;
    private GeoPoint destinazioneCorrente = null;
    private org.osmdroid.views.overlay.Marker currentMarker = null;
    // Gestore moderno dei Permessi
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    attivaPosizioneUtente();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. INIZIALIZZAZIONE MAPPA
        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);if (savedInstanceState != null) {
            // A. Ripristina Zoom e Posizione
            double zoom = savedInstanceState.getDouble("zoom_level", 15.0);
            double lat = savedInstanceState.getDouble("center_lat", 41.8902); // Default Roma
            double lon = savedInstanceState.getDouble("center_lon", 12.4922);

            map.getController().setZoom(zoom);
            map.getController().setCenter(new GeoPoint(lat, lon));

            // B. Ripristina il percorso (se c'era)
            if (savedInstanceState.getBoolean("ha_percorso", false)) {
                double destLat = savedInstanceState.getDouble("dest_lat");
                double destLon = savedInstanceState.getDouble("dest_lon");

                // Dobbiamo aspettare che il GPS si riattivi per avere il punto di partenza
                // Usiamo un piccolo trucco: posticipiamo il calcolo di 1 secondo
                map.postDelayed(() -> {
                    if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                        GeoPoint start = myLocationOverlay.getMyLocation();
                        GeoPoint end = new GeoPoint(destLat, destLon);
                        disegnaPercorsoSullaMappa(start, end);
                    }
                }, 1000);
            }
        } else {
            // Primo avvio assoluto: usa Zoom default
            map.getController().setZoom(18.0);
        }
        View btnCentra = view.findViewById(R.id.fab_centra_posizione);
        if (btnCentra != null) {
            btnCentra.setOnClickListener(v -> {
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    // 1. Porta la mappa su di te con un'animazione fluida
                    map.getController().animateTo(myLocationOverlay.getMyLocation());

                    // 2. Resetta lo zoom a un livello comodo
                    map.getController().setZoom(18.0);

                    // 3. RIAGGANCIA la telecamera (così se ti muovi, la mappa ti segue di nuovo)
                    myLocationOverlay.enableFollowLocation();

                    android.widget.Toast.makeText(requireContext(), "Posizione centrata", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(requireContext(), "Posizione non ancora disponibile", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 2. BOTTONE "GOOGLE MAPS" (Navigazione Esterna)
        android.widget.Button btnGoogle = view.findViewById(R.id.btn_google_maps);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                // Esempio statico su Roma (o potresti passare le coordinate del parcheggio selezionato)
                android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=41.8902,12.4922");
                android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                try {
                    startActivity(mapIntent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "Google Maps non trovato!", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        // --- NOTA: Ho rimosso il listener del bottone "Linea Blu" btn_percorso_interno ---
        // Ora il percorso si attiva scegliendo dalla lista parcheggi.

        // 3. BOTTONE TROVA PARCHEGGI (Overpass API)
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
                // 3. Cerca
                cercaParcheggiVicini(lat, lon);
            });
        }

        // 4. BOTTONE SALVA (FAB)
        com.google.android.material.floatingactionbutton.FloatingActionButton fab = view.findViewById(R.id.fab_salva);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                android.widget.Toast.makeText(requireContext(), "Funzione Salva in arrivo...", android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        btnStopNav = view.findViewById(R.id.btn_stop_navigazione);

        btnStopNav.setOnClickListener(v -> {
            stopNavigazione(); // Chiamiamo la funzione di pulizia
        });

        // 5. GESTIONE PERMESSI GPS
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attivaPosizioneUtente();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    // Questa funzione accende il pallino blu (o l'omino!)
    private void attivaPosizioneUtente() {
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();

        // =================================================================
        // CAMBIAMO L'ICONA CON L'OMINO VETTORIALE
        // =================================================================

        // 1. Convertiamo il Vector (xml) in Bitmap (immagine)
        // Assicurati di aver creato l'icona "ic_baseline_person_24" come spiegato sopra!
        // Se l'hai chiamata diversamente, cambia il nome qui sotto.
        android.graphics.Bitmap omino = getBitmapFromVectorDrawable(requireContext(), R.drawable.baseline_directions_car_24);

        if (omino != null) {
            myLocationOverlay.setPersonIcon(omino);    // Icona quando sei fermo
            myLocationOverlay.setDirectionIcon(omino); // Icona quando ti muovi
        }
        // =================================================================

        map.getOverlays().add(myLocationOverlay);
    }

    // --- FUNZIONE DI SUPPORTO PER CONVERTIRE VETTORI IN BITMAP ---
    public static android.graphics.Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;

        // Se vuoi ingrandire l'omino, cambia questi numeri (es. 100, 100)
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                drawable.getIntrinsicWidth() * 2, // *2 lo rende più grande e visibile
                drawable.getIntrinsicHeight() * 2,
                android.graphics.Bitmap.Config.ARGB_8888);

        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();

        // SALVATAGGIO PERMANENTE (SharedPreferences)
        if (map != null) {
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinPrefs", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            editor.putFloat("last_lat", (float) map.getMapCenter().getLatitude());
            editor.putFloat("last_lon", (float) map.getMapCenter().getLongitude());
            editor.putFloat("last_zoom", (float) map.getZoomLevelDouble());
            editor.apply();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();

        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();

            myLocationOverlay.runOnFirstFix(() -> {
                requireActivity().runOnUiThread(() -> {
                    android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                    boolean eraInNavigazione = prefs.getBoolean("navigazione_attiva", false);

                    if (eraInNavigazione) {
                        double destLat = prefs.getFloat("dest_lat", 0);
                        double destLon = prefs.getFloat("dest_lon", 0);
                        String destNome = prefs.getString("dest_nome", "Destinazione");

                        GeoPoint start = myLocationOverlay.getMyLocation();
                        GeoPoint end = new GeoPoint(destLat, destLon);

                        disegnaPercorsoSullaMappa(start, end);
                        mettiMarkerDestinazione(end, destNome);

                        // --- AGGIUNGI QUESTA RIGA ---
                        btnStopNav.setVisibility(View.VISIBLE); // Fai tornare il bottone!
                        // ----------------------------
                    } else {
                        // Se non c'era navigazione, assicuriamoci che sia nascosto
                        btnStopNav.setVisibility(View.GONE);
                    }
                });
            });
        }
    }

    // =============================================================
    // FUNZIONE DI RICERCA PARCHEGGI
    // =============================================================
    private void cercaParcheggiVicini(double lat, double lon) {
        android.widget.Toast.makeText(requireContext(), "Cerco parcheggi...", android.widget.Toast.LENGTH_SHORT).show();

        retrofit2.Retrofit retrofitOverpass = new retrofit2.Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        OverpassService service = retrofitOverpass.create(OverpassService.class);

        // Cerca parcheggi entro 5000 metri
        String query = "[out:json];node[\"amenity\"=\"parking\"](around:5000," + lat + "," + lon + ");out;";

        service.cercaParcheggi(query).enqueue(new retrofit2.Callback<com.example.parkpin.data.OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().elementi != null) {
                    mostraListaParcheggi(response.body().elementi);
                } else {
                    android.widget.Toast.makeText(requireContext(), "Nessun parcheggio trovato.", android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                android.widget.Toast.makeText(requireContext(), "Errore connessione!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =============================================================
    // MOSTRA LISTA E AVVIA NAVIGAZIONE AL CLICK
    // =============================================================
    private void mostraListaParcheggi(java.util.List<com.example.parkpin.data.OverpassResponse.Elemento> lista) {
        if (lista.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "Nessun parcheggio in zona.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String[] nomiParcheggi = new String[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            com.example.parkpin.data.OverpassResponse.Elemento p = lista.get(i);
            nomiParcheggi[i] = (p.tags != null && p.tags.nome != null) ? p.tags.nome : "Parcheggio (" + (i+1) + ")";
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Seleziona Parcheggio")
                .setItems(nomiParcheggi, (dialog, which) -> {
                    com.example.parkpin.data.OverpassResponse.Elemento selezionato = lista.get(which);
                    String nomeParcheggio = nomiParcheggi[which];

                    GeoPoint puntoArrivo = new GeoPoint(selezionato.lat, selezionato.lon);

                    // 1. Salva in memoria
                    android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putBoolean("navigazione_attiva", true)
                            .putFloat("dest_lat", (float) selezionato.lat)
                            .putFloat("dest_lon", (float) selezionato.lon)
                            .putString("dest_nome", nomeParcheggio)
                            .apply();

                    // --- AGGIUNGI QUESTA RIGA QUI SOTTO ---
                    btnStopNav.setVisibility(View.VISIBLE); // Il bottone rosso appare!
                    // --------------------------------------

                    // 2. Calcola percorso
                    if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                        disegnaPercorsoSullaMappa(myLocationOverlay.getMyLocation(), puntoArrivo);
                    }

                    // 3. Sposta mappa e marker
                    map.getController().animateTo(puntoArrivo);
                    map.getController().setZoom(18.0);
                    mettiMarkerDestinazione(puntoArrivo, nomeParcheggio);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }
    // =============================================================
    // DISEGNA LINEA BLU (Aggiornato per pulire vecchie linee)
    // =============================================================
    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        // 1. SALVIAMO LA DESTINAZIONE NELLA MEMORIA PERMANENTE
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("navigazione_attiva", true)
                .putFloat("dest_lat", (float) end.getLatitude())
                .putFloat("dest_lon", (float) end.getLongitude())
                .apply(); // Salva subito!

        android.widget.Toast.makeText(requireContext(), "Calcolo percorso...", android.widget.Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                Road road = roadManager.getRoad(waypoints);

                if (road.mStatus == Road.STATUS_OK) {
                    Polyline newRoadOverlay = RoadManager.buildRoadOverlay(road);
                    newRoadOverlay.getOutlinePaint().setColor(android.graphics.Color.BLUE);
                    newRoadOverlay.getOutlinePaint().setStrokeWidth(15.0f);

                    requireActivity().runOnUiThread(() -> {
                        if (currentRoute != null) map.getOverlays().remove(currentRoute);
                        currentRoute = newRoadOverlay;
                        map.getOverlays().add(currentRoute);
                        map.invalidate();
                        map.zoomToBoundingBox(road.mBoundingBox, true);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (map != null) {
            // 1. Salva Zoom e Centro Mappa
            outState.putDouble("zoom_level", map.getZoomLevelDouble());
            outState.putDouble("center_lat", map.getMapCenter().getLatitude());
            outState.putDouble("center_lon", map.getMapCenter().getLongitude());

            // 2. Salva il percorso (se esiste)
            if (destinazioneCorrente != null) {
                outState.putBoolean("ha_percorso", true);
                outState.putDouble("dest_lat", destinazioneCorrente.getLatitude());
                outState.putDouble("dest_lon", destinazioneCorrente.getLongitude());
            }
        }
    }
    // Funzione che mette (o rimette) il marker rosso sulla mappa
    private void mettiMarkerDestinazione(GeoPoint punto, String titolo) {
        // 1. Se c'era già un vecchio marker, toglilo (pulizia)
        if (currentMarker != null) {
            map.getOverlays().remove(currentMarker);
        }

        // 2. Crea il nuovo marker
        currentMarker = new org.osmdroid.views.overlay.Marker(map);
        currentMarker.setPosition(punto);
        currentMarker.setTitle("Destinazione");
        currentMarker.setSnippet(titolo);
        currentMarker.setIcon(androidx.core.content.ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default));

        // 3. Aggiungi alla mappa e ridisegna
        map.getOverlays().add(currentMarker);
        map.invalidate();
    }
    private void stopNavigazione() {
        // 1. Pulisci la memoria (così non ricompare se ruoti)
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        // 2. Rimuovi la linea blu
        if (currentRoute != null) {
            map.getOverlays().remove(currentRoute);
            currentRoute = null;
        }

        // 3. Rimuovi il marker destinazione
        if (currentMarker != null) {
            map.getOverlays().remove(currentMarker);
            currentMarker = null;
        }

        // 4. Nascondi il bottone Stop
        btnStopNav.setVisibility(View.GONE);

        // 5. Aggiorna la mappa
        map.invalidate();
        android.widget.Toast.makeText(requireContext(), "Navigazione annullata", android.widget.Toast.LENGTH_SHORT).show();
    }
}