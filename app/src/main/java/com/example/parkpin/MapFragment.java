package com.example.parkpin;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;

// Osmdroid Imports
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;

import java.util.ArrayList;
import java.util.List;

// Retrofit Imports
import com.example.parkpin.data.OverpassResponse;
import com.example.parkpin.data.OverpassResponse.Elemento; // Assicurati che la tua classe si chiami così
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MapFragment extends Fragment {

    // --- VARIABILI MAPPA ---
    private MapView map = null;
    private MyLocationNewOverlay myLocationOverlay; // Il cursore (Auto/Pallino)
    private Polyline currentRoute = null; // La linea blu
    private Marker currentMarker = null; // Il pin rosso di destinazione

    // --- VARIABILI NAVIGAZIONE ---
    private android.widget.Button btnStopNav;
    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;
    private GeoPoint ultimaPosizioneCalcolo = null; // Per evitare ricalcoli inutili

    // --- INTERFACCIA RETROFIT (Definita qui per comodità) ---
    public interface OverpassService {
        @GET("interpreter")
        Call<OverpassResponse> cercaParcheggi(@Query("data") String data);
    }

    // --- GESTORE PERMESSI ---
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    attivaPosizioneUtente();
                } else {
                    Toast.makeText(requireContext(), "Serve il GPS per navigare!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        // Imposta uno User Agent per evitare blocchi da OSM
        Configuration.getInstance().setUserAgentValue("ParkPinApp/1.0");
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. INIZIALIZZAZIONE MAPPA
        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        // 2. RIPRISTINO STATO (Se giri lo schermo)
        if (savedInstanceState != null) {
            double zoom = savedInstanceState.getDouble("zoom_level", 18.0);
            double lat = savedInstanceState.getDouble("center_lat", 41.8902);
            double lon = savedInstanceState.getDouble("center_lon", 12.4922);
            map.getController().setZoom(zoom);
            map.getController().setCenter(new GeoPoint(lat, lon));
        } else {
            map.getController().setZoom(18.0);
        }

        // 3. SETUP BOTTONI

        // A. Bottone Centra Posizione (Mirino)
        View btnCentra = view.findViewById(R.id.fab_centra_posizione);
        if (btnCentra != null) {
            btnCentra.setOnClickListener(v -> {
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    map.getController().animateTo(myLocationOverlay.getMyLocation());
                    map.getController().setZoom(18.0);
                    myLocationOverlay.enableFollowLocation();
                } else {
                    Toast.makeText(requireContext(), "Attendi segnale GPS...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // B. Bottone Google Maps (In alto a destra nella barra)
        View btnGoogle = view.findViewById(R.id.btn_google_maps); // Nota: Potrebbe essere ImageButton o Button
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> {
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                if (prefs.getBoolean("navigazione_attiva", false)) {
                    float lat = prefs.getFloat("dest_lat", 0);
                    float lon = prefs.getFloat("dest_lon", 0);
                    android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=" + lat + "," + lon);
                    android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    try {
                        startActivity(mapIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Google Maps non installato.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Seleziona prima un parcheggio!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // C. Bottone Cerca Parcheggi (Overpass)
        View btnCerca = view.findViewById(R.id.btn_cerca_parcheggi);
        if (btnCerca != null) {
            btnCerca.setOnClickListener(v -> {
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    double lat = myLocationOverlay.getMyLocation().getLatitude();
                    double lon = myLocationOverlay.getMyLocation().getLongitude();
                    cercaParcheggiVicini(lat, lon);
                } else {
                    Toast.makeText(requireContext(), "GPS non ancora pronto.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // D. Bottone Salva (+)
        View fabSalva = view.findViewById(R.id.fab_salva);
        if (fabSalva != null) {
            fabSalva.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Funzione Salva Auto in arrivo...", Toast.LENGTH_SHORT).show();
                // Qui in futuro metteremo il codice per il Database Room
            });
        }

        // E. Bottone STOP Navigazione (Rosso)
        btnStopNav = view.findViewById(R.id.btn_stop_navigazione);
        btnStopNav.setOnClickListener(v -> stopNavigazione());

        // 4. ATTIVAZIONE GPS
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attivaPosizioneUtente();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    // =============================================================
    // GESTIONE GPS E NAVIGAZIONE REAL-TIME
    // =============================================================
    private void attivaPosizioneUtente() {
        // 1. Setup Overlay su Mappa
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();

        // Icona Macchina
        android.graphics.Bitmap iconaMacchina = getBitmapFromVectorDrawable(requireContext(), R.drawable.baseline_directions_car_24);
        // Assicurati che il nome drawable corrisponda al tuo file (es. logo_parkpin o car)

        if (iconaMacchina != null) {
            myLocationOverlay.setPersonIcon(iconaMacchina);
            myLocationOverlay.setDirectionIcon(iconaMacchina);
        }
        map.getOverlays().add(myLocationOverlay);

        // 2. Setup Listener per aggiornamenti posizione e calcolo arrivo
        locationManager = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        locationListener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(@NonNull android.location.Location location) {
                // Controllo se stiamo navigando
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                if (!prefs.getBoolean("navigazione_attiva", false)) return;

                double destLat = prefs.getFloat("dest_lat", 0);
                double destLon = prefs.getFloat("dest_lon", 0);

                // Calcolo distanza rimanente
                float[] risultati = new float[1];
                android.location.Location.distanceBetween(location.getLatitude(), location.getLongitude(), destLat, destLon, risultati);
                float distanzaMetri = risultati[0];

                // CASO 1: SEI ARRIVATO (< 40m)
                if (distanzaMetri < 40) {
                    gestisciArrivoDestinazione();
                }
                // CASO 2: AGGIORNA PERCORSO (se ti sei mosso > 50m dall'ultimo calcolo)
                else {
                    GeoPoint posAttuale = new GeoPoint(location.getLatitude(), location.getLongitude());
                    if (ultimaPosizioneCalcolo == null || posAttuale.distanceToAsDouble(ultimaPosizioneCalcolo) > 50) {
                        GeoPoint destinazione = new GeoPoint(destLat, destLon);
                        disegnaPercorsoSullaMappa(posAttuale, destinazione); // Ricalcola linea blu
                        ultimaPosizioneCalcolo = posAttuale;
                    }
                }
            }
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        // Attiva aggiornamenti (ogni 2 sec o 10 metri)
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000, 10, locationListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void gestisciArrivoDestinazione() {
        stopNavigazione();
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("🎉 DESTINAZIONE RAGGIUNTA!")
                .setMessage("Sei arrivato al parcheggio. Buona sosta con ParkPin!")
                .setIcon(android.R.drawable.ic_dialog_map)
                .setPositiveButton("Chiudi", null)
                .show();

        // Vibrazione
        android.os.Vibrator v = (android.os.Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) v.vibrate(500);
    }

    // =============================================================
    // RICERCA PARCHEGGI (RETROFIT + OVERPASS)
    // =============================================================
    private void cercaParcheggiVicini(double lat, double lon) {
        Toast.makeText(requireContext(), "Cerco parcheggi in zona...", Toast.LENGTH_SHORT).show();

        retrofit2.Retrofit retrofitOverpass = new retrofit2.Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        OverpassService service = retrofitOverpass.create(OverpassService.class);

        // Query Overpass: cerca parcheggi entro 2km
        String query = "[out:json];node[\"amenity\"=\"parking\"](around:2000," + lat + "," + lon + ");out;";

        service.cercaParcheggi(query).enqueue(new retrofit2.Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().elementi != null) {
                    // CHIAMATA AL NUOVO METODO MIGLIORATO
                    mostraListaParcheggiMigliorata(response.body().elementi);
                } else {
                    Toast.makeText(requireContext(), "Nessun parcheggio trovato.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                Toast.makeText(requireContext(), "Errore connessione: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =============================================================
    // NUOVO METODO LISTA: BOTTOM SHEET + CARDS
    // =============================================================
    private void mostraListaParcheggiMigliorata(java.util.List<Elemento> listaParcheggi) {
        if (listaParcheggi == null || listaParcheggi.isEmpty()) {
            Toast.makeText(requireContext(), "Nessun parcheggio in zona.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());

        // --- 1. CONFIGURAZIONE CONTENITORE ---
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        // Colore di sfondo generale del pannello (Grigio chiarissimo per far risaltare le card bianche)
        container.setBackgroundResource(R.drawable.sfondo_bottom_sheet);
        container.setPadding(0, 30, 0, 50); // Padding

        // --- 2. AGGIUNTA "MANIGLIA" (Barretta grigia in alto) ---
        View handle = new View(requireContext());
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(100, 12); // Larghezza 100, Altezza 12
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = 40;
        handle.setLayoutParams(handleParams);
        handle.setBackgroundColor(Color.parseColor("#E0E0E0")); // Grigio chiaro
        // Arrotondiamo la maniglia (trucco veloce usando un drawable standard o shape)
        // Per semplicità qui è rettangolare, ma sta bene lo stesso.
        container.addView(handle);

        // Titolo elegante
        TextView titolo = new TextView(requireContext());
        titolo.setText("Parcheggi nelle vicinanze");
        titolo.setTextSize(20);
        titolo.setTypeface(null, Typeface.BOLD);
        titolo.setTextColor(Color.BLACK);
        titolo.setPadding(40, 0, 0, 30); // Padding sinistro allineato alle card
        container.addView(titolo);

        // --- 3. CICLO ELEMENTI ---
        for (Elemento p : listaParcheggi) {
            if (p.lat == 0 || p.lon == 0) continue;

            // Parsing Dati
            String nome = "Parcheggio Pubblico";

            // Logica NOME
            if (p.tags != null) {
                if (p.tags.nome != null) nome = p.tags.nome;
                else if (p.tags.operator != null) nome = "Parcheggio " + p.tags.operator;
            }

            // Logica COSTO (Fee)
            boolean isPagamento = false;
            String testoPrezzo = "GRATIS";
            String posti = "";

            if (p.tags != null) {
                String fee = p.tags.fee;
                if ("yes".equals(fee) || (fee != null && fee.contains("pay"))) {
                    isPagamento = true;
                    testoPrezzo = "PAGAMENTO";
                }

                if (p.tags.capacity != null) {
                    posti = " • " + p.tags.capacity + " posti";
                }
            }

            // --- INFLATING LAYOUT ---
            View card = getLayoutInflater().inflate(R.layout.item_parcheggio, null);

            TextView txtNome = card.findViewById(R.id.txt_nome_parcheggio);
            TextView txtPrezzo = card.findViewById(R.id.txt_prezzo);
            TextView txtDettagli = card.findViewById(R.id.txt_dettagli_parcheggio);

            txtNome.setText(nome);
            txtDettagli.setText(posti);
            txtPrezzo.setText(testoPrezzo);

            // --- GESTIONE COLORI INTELLIGENTE ---
            if (isPagamento) {
                // Se paga: Scritta ARANCIONE SCURO, Sfondo ARANCIONE CHIARO
                txtPrezzo.setTextColor(Color.parseColor("#E65100")); // Arancione scuro
                // txtPrezzo.setBackgroundColor(Color.parseColor("#FFE0B2")); // Opzionale sfondo
            } else {
                // Se gratis: Scritta VERDE SCURO, Sfondo VERDE CHIARO
                txtPrezzo.setTextColor(Color.parseColor("#2E7D32")); // Verde scuro
                // txtPrezzo.setBackgroundColor(Color.parseColor("#C8E6C9")); // Opzionale sfondo
            }

            // --- CLICK ---
            String finalNome = nome;
            card.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                GeoPoint dest = new GeoPoint(p.lat, p.lon);

                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("navigazione_attiva", true)
                        .putFloat("dest_lat", (float) p.lat)
                        .putFloat("dest_lon", (float) p.lon)
                        .apply();

                if (btnStopNav != null) btnStopNav.setVisibility(View.VISIBLE);

                map.getController().animateTo(dest);
                map.getController().setZoom(18.5);
                mettiMarkerDestinazione(dest, finalNome);
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    disegnaPercorsoSullaMappa(myLocationOverlay.getMyLocation(), dest);
                }
                Toast.makeText(requireContext(), "Navigazione verso: " + finalNome, Toast.LENGTH_SHORT).show();
            });

            container.addView(card);
        }

        scrollView.addView(container);
        bottomSheetDialog.setContentView(scrollView);

        // TRUCCO FONDAMENTALE PER GLI ANGOLI ARROTONDATI
        // Rende trasparente il contenitore standard di Android, così si vede solo il tuo sfondo curvo.
        bottomSheetDialog.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            android.view.View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        bottomSheetDialog.show();
    }

    // =============================================================
    // METODI GRAFICI E UTILITÀ
    // =============================================================
    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);
                Road road = roadManager.getRoad(waypoints);

                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(Color.BLUE);
                    roadOverlay.getOutlinePaint().setStrokeWidth(15.0f);

                    requireActivity().runOnUiThread(() -> {
                        if (currentRoute != null) map.getOverlays().remove(currentRoute);
                        currentRoute = roadOverlay;
                        map.getOverlays().add(currentRoute);
                        map.invalidate();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void mettiMarkerDestinazione(GeoPoint punto, String titolo) {
        if (currentMarker != null) map.getOverlays().remove(currentMarker);

        currentMarker = new Marker(map);
        currentMarker.setPosition(punto);
        currentMarker.setTitle("Arrivo");
        currentMarker.setSnippet(titolo);
        currentMarker.setIcon(ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default));
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        map.getOverlays().add(currentMarker);
        map.invalidate();
    }

    private void stopNavigazione() {
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        ultimaPosizioneCalcolo = null;

        if (currentRoute != null) {
            map.getOverlays().remove(currentRoute);
            currentRoute = null;
        }
        if (currentMarker != null) {
            map.getOverlays().remove(currentMarker);
            currentMarker = null;
        }
        btnStopNav.setVisibility(View.GONE);
        map.invalidate();
        Toast.makeText(requireContext(), "Navigazione terminata.", Toast.LENGTH_SHORT).show();
    }

    public static android.graphics.Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                drawable.getIntrinsicWidth() * 2, drawable.getIntrinsicHeight() * 2, android.graphics.Bitmap.Config.ARGB_8888);
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
        if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
            // Controlla se c'era navigazione attiva e ripristina bottone
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
            if (prefs.getBoolean("navigazione_attiva", false)) {
                btnStopNav.setVisibility(View.VISIBLE);
            }
        }
    }
}