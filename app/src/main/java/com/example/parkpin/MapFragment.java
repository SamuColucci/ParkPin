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
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

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
import com.example.parkpin.data.OverpassResponse.Elemento;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MapFragment extends Fragment {

    // --- VARIABILI MAPPA ---
    private MapView map = null;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline currentRoute = null;
    private Marker currentMarker = null;

    // --- VARIABILI NAVIGAZIONE ---
    private android.widget.Button btnStopNav;
    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;
    private GeoPoint ultimaPosizioneCalcolo = null;

    // --- RETROFIT INTERFACE ---
    public interface OverpassService {
        @GET("interpreter")
        Call<OverpassResponse> cercaParcheggi(@Query("data") String data);
    }

    // --- PERMESSI ---
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
        Configuration.getInstance().setUserAgentValue("ParkPinApp/1.0");
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. MAPPA SETUP
        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        if (savedInstanceState != null) {
            map.getController().setZoom(savedInstanceState.getDouble("zoom_level", 18.0));
            map.getController().setCenter(new GeoPoint(
                    savedInstanceState.getDouble("center_lat", 41.8902),
                    savedInstanceState.getDouble("center_lon", 12.4922)));
        } else {
            map.getController().setZoom(18.0);
        }

        // 2. BOTTONI

        // A. Centra
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

        // B. Google Maps
        View btnNavGoogle = view.findViewById(R.id.btn_nav_google_maps);

        if (btnNavGoogle != null) {
            btnNavGoogle.setOnClickListener(v -> {
                // Recuperiamo le coordinate della destinazione ATTUALE
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

                if (prefs.getBoolean("navigazione_attiva", false)) {
                    float lat = prefs.getFloat("dest_lat", 0);
                    float lon = prefs.getFloat("dest_lon", 0);

                    // Apriamo Google Maps Navigation verso quel punto
                    android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=" + lat + "," + lon);
                    android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");

                    try {
                        startActivity(mapIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Google Maps non installato.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Questo caso non dovrebbe succedere perché il pannello è nascosto se non navighi
                    Toast.makeText(requireContext(), "Nessuna destinazione attiva.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // C. CERCA PARCHEGGI (MODIFICATO: APRE IL FILTRO)
        View btnCerca = view.findViewById(R.id.btn_cerca_parcheggi);
        if (btnCerca != null) {
            btnCerca.setOnClickListener(v -> {
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    // Invece di cercare subito, apriamo il dialog dei filtri
                    mostraDialogFiltri(myLocationOverlay.getMyLocation().getLatitude(), myLocationOverlay.getMyLocation().getLongitude());
                } else {
                    Toast.makeText(requireContext(), "GPS non pronto. Attendi...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // D. Salva
        View fabSalva = view.findViewById(R.id.fab_salva);
        if (fabSalva != null) {
            fabSalva.setOnClickListener(v -> {
                // CONTROLLO INTENT (Se arrivo dalla Welcome con "Salva posizione")
                Toast.makeText(requireContext(), "Funzione Salva Auto in arrivo...", Toast.LENGTH_SHORT).show();
            });
        }

        // E. Stop Navigazione
        btnStopNav = view.findViewById(R.id.btn_stop_navigazione);
        btnStopNav.setOnClickListener(v -> stopNavigazione());

        // 3. GPS START
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attivaPosizioneUtente();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void mostraDialogFiltri(double lat, double lon) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_search_filters, null);
        dialog.setContentView(view);
        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);

        // Componenti
        android.widget.EditText etNome = view.findViewById(R.id.et_ricerca_nome); // NUOVO CAMPO
        TextView txtRaggio = view.findViewById(R.id.txt_raggio_label);
        SeekBar seekBar = view.findViewById(R.id.seekbar_distanza);
        RadioGroup radioGroup = view.findViewById(R.id.radio_group_tipo);
        MaterialButton btnAvvia = view.findViewById(R.id.btn_avvia_ricerca);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 500) progress = 500;
                txtRaggio.setText("Distanza massima: " + (progress / 1000.0) + " km");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnAvvia.setOnClickListener(v -> {
            int raggio = seekBar.getProgress();
            if (raggio < 500) raggio = 500;

            String filtroCosto = "all";
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.radio_gratis) filtroCosto = "free";
            else if (selectedId == R.id.radio_pagamento) filtroCosto = "paid";

            // LEGGIAMO IL NOME (e togliamo spazi extra)
            String filtroNome = etNome.getText().toString().trim();

            dialog.dismiss();
            // Passiamo anche il nome alla ricerca
            cercaParcheggiVicini(lat, lon, raggio, filtroCosto, filtroNome);
        });

        dialog.show();
    }

    // =============================================================
    // RICERCA: SCARICA TUTTO E FILTRA DOPO (Più affidabile)
    // =============================================================
    // Aggiunto parametro filtroNome
    private void cercaParcheggiVicini(double lat, double lon, int raggio, String filtroCosto, String filtroNome) {
        Toast.makeText(requireContext(), "Cerco parcheggi...", Toast.LENGTH_SHORT).show();

        retrofit2.Retrofit retrofitOverpass = new retrofit2.Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();

        OverpassService service = retrofitOverpass.create(OverpassService.class);

        // Scarichiamo SEMPRE tutto, il filtro nome lo facciamo in locale
        String query = "[out:json];node[\"amenity\"=\"parking\"](around:" + raggio + "," + lat + "," + lon + ");out;";

        service.cercaParcheggi(query).enqueue(new retrofit2.Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().elementi != null) {
                    if (response.body().elementi.isEmpty()) {
                        Toast.makeText(requireContext(), "Nessun parcheggio trovato.", Toast.LENGTH_LONG).show();
                    } else {
                        // Passiamo TUTTI i filtri alla lista
                        mostraListaParcheggiMigliorata(response.body().elementi, filtroCosto, filtroNome);
                    }
                } else {
                    Toast.makeText(requireContext(), "Errore ricerca.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                Toast.makeText(requireContext(), "Errore connessione: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    // =============================================================
    // NAVIGAZIONE E LISTE (CODICE PRECEDENTE MANTENUTO)
    // =============================================================

    private void attivaPosizioneUtente() {
        // 1. Setup Overlay su Mappa (Macchinina)
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();

        android.graphics.Bitmap iconaMacchina = getBitmapFromVectorDrawable(requireContext(), R.drawable.baseline_directions_car_24);
        if (iconaMacchina != null) {
            myLocationOverlay.setPersonIcon(iconaMacchina);
            myLocationOverlay.setDirectionIcon(iconaMacchina);
        }
        map.getOverlays().add(myLocationOverlay);

        // 2. Setup Listener GPS
        locationManager = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        locationListener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(@NonNull android.location.Location location) {
                // Controllo se stiamo navigando
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                // Se la navigazione non è attiva, usciamo subito
                if (!prefs.getBoolean("navigazione_attiva", false)) return;

                double destLat = prefs.getFloat("dest_lat", 0);
                double destLon = prefs.getFloat("dest_lon", 0);

                // Calcolo distanza rimanente in metri
                float[] risultati = new float[1];
                android.location.Location.distanceBetween(location.getLatitude(), location.getLongitude(), destLat, destLon, risultati);
                float distanzaMetri = risultati[0];

                // =============================================================
                // NUOVO: AGGIORNAMENTO UI IN TEMPO REALE (Scritta nel Pannello Verde) 🟢
                // =============================================================
                if (getView() != null) {
                    TextView txtDist = getView().findViewById(R.id.txt_nav_distanza);
                    View navPanel = getView().findViewById(R.id.nav_info_card);

                    // Aggiorniamo solo se il pannello è visibile
                    if (txtDist != null && navPanel != null && navPanel.getVisibility() == View.VISIBLE) {
                        if (distanzaMetri < 1000) {
                            txtDist.setText((int) distanzaMetri + " m all'arrivo");
                        } else {
                            txtDist.setText(String.format("%.1f km all'arrivo", distanzaMetri / 1000));
                        }
                    }
                }
                // =============================================================


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
                .setTitle("🎉 ARRIVATO!")
                .setMessage("Sei giunto a destinazione.")
                .setIcon(android.R.drawable.ic_dialog_map)
                .setPositiveButton("Ok", null)
                .show();
        android.os.Vibrator v = (android.os.Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) v.vibrate(500);
    }

    // =============================================================
    // LISTA CON FILTRO LOCALE E DISTANZA
    // =============================================================
    // Nota: Ho aggiunto il parametro String filtroCosto
    // Aggiunto parametro filtroNome
    // =============================================================
    // LISTA DEFINITIVA (Con Via, Distanza Aerea e Filtri) 🏆
    // =============================================================
    private void mostraListaParcheggiMigliorata(java.util.List<Elemento> listaParcheggi, String filtroCosto, String filtroNome) {
        if (listaParcheggi == null || listaParcheggi.isEmpty()) return;

        int conteggioVisibili = 0;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.sfondo_bottom_sheet);
        container.setPadding(0, 30, 0, 50);

        // Maniglia
        View handle = new View(requireContext());
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(100, 12);
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = 40;
        handle.setLayoutParams(handleParams);
        handle.setBackgroundColor(Color.parseColor("#E0E0E0"));
        container.addView(handle);

        // Titolo
        TextView titolo = new TextView(requireContext());
        titolo.setTextSize(20);
        titolo.setTypeface(null, Typeface.BOLD);
        titolo.setTextColor(Color.BLACK);
        titolo.setPadding(40, 0, 0, 30);
        container.addView(titolo);

        // Recupera posizione attuale per calcolo distanza aerea
        GeoPoint miaPosizione = null;
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            miaPosizione = myLocationOverlay.getMyLocation();
        }

        for (Elemento p : listaParcheggi) {
            if (p.lat == 0 || p.lon == 0) continue;

            // --- 1. PARSING NOME E VIA ---
            // --- 1. PARSING NOME E VIA 🏠 ---
            String nome = "Parcheggio Pubblico";

            // Logica Nome (uguale a prima)
            if (p.tags != null) {
                if (p.tags.nome != null) nome = p.tags.nome;
                else if (p.tags.operator != null) nome = "Parcheggio " + p.tags.operator;
            }

            // --- NUOVA LOGICA INDIRIZZO (SMART) ---
            String strada = null;

            // TENTATIVO A: Leggiamo dai Tag di OSM (è il più veloce)
            if (p.tags != null && p.tags.strada != null) {
                strada = p.tags.strada;
                if (p.tags.numeroCivico != null) strada += ", " + p.tags.numeroCivico;
            }

            // TENTATIVO B: Se OSM è vuoto, usiamo il Geocoder Android (più lento ma preciso)
            if (strada == null || strada.isEmpty()) {
                // Chiamiamo il nostro metodo helper
                strada = ottieniIndirizzoReale(p.lat, p.lon);
            }

            // Se fallisce pure quello, mettiamo un default
            if (strada == null) strada = "Posizione sulla mappa";

            // --- 2. PARSING COSTO ---
            boolean isPagamento = false;
            String testoPrezzo = "GRATIS";
            String dettagliExtra = "";

            if (p.tags != null) {
                String fee = p.tags.fee;
                if ("yes".equals(fee) || (fee != null && fee.contains("pay"))) {
                    isPagamento = true;
                    testoPrezzo = "PAGAMENTO";
                }
                if (p.tags.capacity != null) dettagliExtra = " • " + p.tags.capacity + " posti";
            }

            // --- 3. FILTRI UTENTE ---
            // Filtro Costo
            if (filtroCosto.equals("free") && isPagamento) continue;
            if (filtroCosto.equals("paid") && !isPagamento) continue;

            // Filtro Testo (Cerca in Nome o Via)
            if (!filtroNome.isEmpty()) {
                String ricerca = filtroNome.toLowerCase();
                if (!nome.toLowerCase().contains(ricerca) && !strada.toLowerCase().contains(ricerca)) {
                    continue;
                }
            }

            conteggioVisibili++;

            // --- 4. CALCOLO DISTANZA (LINEA D'ARIA) ---
            // Usiamo il simbolo "~" per indicare che è approssimativa (linea d'aria)
            String testoDistanza = "";
            if (miaPosizione != null) {
                GeoPoint posParcheggio = new GeoPoint(p.lat, p.lon);
                double distanzaMetri = miaPosizione.distanceToAsDouble(posParcheggio);

                if (distanzaMetri < 1000) {
                    testoDistanza = String.format(" • ~%d m", (int) distanzaMetri);
                } else {
                    testoDistanza = String.format(" • ~%.1f km", distanzaMetri / 1000);
                }
            }

            // --- 5. CREAZIONE GRAFICA ---
            View card = getLayoutInflater().inflate(R.layout.item_parcheggio, null);
            TextView txtNome = card.findViewById(R.id.txt_nome_parcheggio);
            TextView txtIndirizzo = card.findViewById(R.id.txt_indirizzo); // Assicurati di aver aggiornato l'XML item_parcheggio
            TextView txtPrezzo = card.findViewById(R.id.txt_prezzo);
            TextView txtDettagli = card.findViewById(R.id.txt_dettagli_parcheggio);

            // Composizione stringa dettagli
            String infoFinale = dettagliExtra + testoDistanza;
            if (dettagliExtra.isEmpty() && !testoDistanza.isEmpty()) {
                infoFinale = testoDistanza.replace(" • ", "");
            }

            txtNome.setText(nome);
            txtIndirizzo.setText(strada); // Mostra la via
            txtDettagli.setText(infoFinale); // Mostra posti e distanza
            txtPrezzo.setText(testoPrezzo);

            // Colori Prezzo
            if (isPagamento) txtPrezzo.setTextColor(Color.parseColor("#E65100"));
            else txtPrezzo.setTextColor(Color.parseColor("#2E7D32"));

            // --- CLICK LISTENER ---
            String finalNome = nome;
            card.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                GeoPoint dest = new GeoPoint(p.lat, p.lon);

                // Salva Preferenze
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("navigazione_attiva", true)
                        .putFloat("dest_lat", (float) p.lat)
                        .putFloat("dest_lon", (float) p.lon)
                        .putString("dest_nome", finalNome)
                        .apply();

                // Mostra Pannello Navigazione Verde
                aggiornaUI_Navigazione(true);

                // Muovi Mappa
                map.getController().animateTo(dest);
                map.getController().setZoom(18.5);
                mettiMarkerDestinazione(dest, finalNome);

                // Calcola e Disegna Percorso (Qui otterremo la distanza REALE nel pannello verde)
                if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                    disegnaPercorsoSullaMappa(myLocationOverlay.getMyLocation(), dest);
                }

                Toast.makeText(requireContext(), "Rotta verso: " + finalNome, Toast.LENGTH_SHORT).show();
            });

            container.addView(card);
        }

        // Titolo dinamico
        if (conteggioVisibili == 0) {
            titolo.setText("Nessun parcheggio trovato.");
        } else {
            titolo.setText("Risultati (" + conteggioVisibili + ")");
        }

        scrollView.addView(container);
        bottomSheetDialog.setContentView(scrollView);
        ((View) scrollView.getParent()).setBackgroundColor(Color.TRANSPARENT);
        bottomSheetDialog.show();
    }
    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                // Qui OSRM calcola la strada vera
                Road road = roadManager.getRoad(waypoints);

                if (road.mStatus == Road.STATUS_OK) {
                    // 1. Disegna la linea blu
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(Color.BLUE);
                    roadOverlay.getOutlinePaint().setStrokeWidth(15.0f);

                    // 2. Prendi la distanza REALE (in km)
                    double distanzaRealeKm = road.mLength;

                    requireActivity().runOnUiThread(() -> {
                        // A. Metti la linea sulla mappa
                        if (currentRoute != null) map.getOverlays().remove(currentRoute);
                        currentRoute = roadOverlay;
                        map.getOverlays().add(currentRoute);
                        map.invalidate();

                        // B. AGGIORNA IL PANNELLO VERDE CON LA DISTANZA REALE 🚗
                        TextView txtDist = getView().findViewById(R.id.txt_nav_distanza);
                        if (txtDist != null && getView().findViewById(R.id.nav_info_card).getVisibility() == View.VISIBLE) {
                            if (distanzaRealeKm < 1.0) {
                                // Se meno di 1km, converti in metri (es. 0.4 km -> 400 m)
                                txtDist.setText(String.format("%d m (stradale)", (int)(distanzaRealeKm * 1000)));
                            } else {
                                txtDist.setText(String.format("%.1f km (stradale)", distanzaRealeKm));
                            }
                        }
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
        // 1. Pulisci preferenze
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        ultimaPosizioneCalcolo = null;

        // 2. Rimuovi linee e marker
        if (currentRoute != null) {
            map.getOverlays().remove(currentRoute);
            currentRoute = null;
        }
        if (currentMarker != null) {
            map.getOverlays().remove(currentMarker);
            currentMarker = null;
        }

        // 3. Nascondi bottone Stop
        btnStopNav.setVisibility(View.GONE);

        // =========================================================
        // 4. NUOVO: RESETTA LA BARRA DI RICERCA 🔄
        // =========================================================
        TextView searchBar = getView().findViewById(R.id.btn_cerca_parcheggi);
        if (searchBar != null) {
            searchBar.setText("Cerca parcheggio qui..."); // Testo di default
            searchBar.setTextColor(Color.parseColor("#37474F")); // Grigio scuro originale
            searchBar.setTypeface(null, Typeface.NORMAL); // Toglie il grassetto
        }
        // =========================================================
        aggiornaUI_Navigazione(false);
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
        }

        // ====================================================================
        // RIPRISTINO STATO NAVIGAZIONE (SE L'APP ERA CHIUSA) 🔄
        // ====================================================================
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

        if (prefs.getBoolean("navigazione_attiva", false)) {
            // 1. Recupera i dati salvati
            double lat = prefs.getFloat("dest_lat", 0);
            double lon = prefs.getFloat("dest_lon", 0);
            String nomeDest = prefs.getString("dest_nome", "Destinazione");
            GeoPoint dest = new GeoPoint(lat, lon);

            // 2. Ripristina il Pannello Verde
            aggiornaUI_Navigazione(true);

            // 3. RIPRISTINA IL MARKER ROSSO 📍 (Questo mancava!)
            mettiMarkerDestinazione(dest, nomeDest);

            // 4. Ripristina la linea blu (Se abbiamo già la posizione GPS)
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                disegnaPercorsoSullaMappa(myLocationOverlay.getMyLocation(), dest);
            }
            // Se il GPS non è ancora pronto, ci penserà 'onLocationChanged' a disegnare la linea
            // appena trova la posizione, ma il marker rosso sarà già lì visibile.

        } else {
            // Nessuna navigazione: assicuriamoci che la UI sia pulita
            aggiornaUI_Navigazione(false);
        }
        // ====================================================================
    }
    private void aggiornaUI_Navigazione(boolean attiva) {
        View searchBar = getView().findViewById(R.id.search_bar_card);
        View navPanel = getView().findViewById(R.id.nav_info_card);
        View btnStop = getView().findViewById(R.id.btn_stop_navigazione);

        if (searchBar == null || navPanel == null) return;

        if (attiva) {
            // MOSTRA NAVIGAZIONE
            searchBar.setVisibility(View.GONE);  // Nascondi Ricerca
            navPanel.setVisibility(View.VISIBLE); // Mostra Verde
            if (btnStop != null) btnStop.setVisibility(View.VISIBLE);

            // Recupera nome parcheggio dalle preferenze
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
            String nomeDest = prefs.getString("dest_nome", "Destinazione"); // Assicurati di salvare il nome!

            TextView txtDest = navPanel.findViewById(R.id.txt_nav_destinazione);
            if (txtDest != null) txtDest.setText(nomeDest);

        } else {
            // MOSTRA RICERCA NORMALE
            searchBar.setVisibility(View.VISIBLE);
            navPanel.setVisibility(View.GONE);
            if (btnStop != null) btnStop.setVisibility(View.GONE);
        }
    }
    // =============================================================
    // HELPER: TROVA INDIRIZZO REALE DA COORDINATE (Reverse Geocoding) 🌍
    // =============================================================
    private String ottieniIndirizzoReale(double lat, double lon) {
        String indirizzoTrovato = "Zona non specificata";
        try {
            android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(), java.util.Locale.getDefault());
            // Chiediamo max 1 risultato
            java.util.List<android.location.Address> addresses = geocoder.getFromLocation(lat, lon, 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address obj = addresses.get(0);
                // Thoroughfare è il nome della via (es. Via Roma)
                if (obj.getThoroughfare() != null) {
                    indirizzoTrovato = obj.getThoroughfare();
                    // SubThoroughfare è il numero civico (es. 10)
                    if (obj.getSubThoroughfare() != null) {
                        indirizzoTrovato += ", " + obj.getSubThoroughfare();
                    }
                }
                // Se non trova la via, prova almeno con la località
                else if (obj.getLocality() != null) {
                    indirizzoTrovato = obj.getLocality();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Se fallisce (es. niente internet), lascia il default
        }
        return indirizzoTrovato;
    }
}