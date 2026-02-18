package com.example.parkpin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import com.example.parkpin.data.OverpassResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements LocationListener {

    public interface OverpassService {
        @GET("interpreter")
        Call<OverpassResponse> cercaParcheggi(@Query("data") String data);
    }

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private TextView txtCriteriAttivi;
    private RecyclerView recyclerViewResults;

    // UI ELEMENTS
    private LinearLayout loadingContainer;
    private LinearLayout layoutError;
    private TextView txtErrorMsg;
    private Button btnRetry;
    private Button btnToggleList; // NUOVO

    private List<OverpassResponse.Elemento> tuttiParcheggiScaricati = new ArrayList<>();
    private List<Marker> markersAttuali = new ArrayList<>();
    private ParcheggioAdapter adapter;
    private LocationManager locationManager;

    private boolean isPrimoCaricamentoEffettuato = false;
    private boolean isListaVisibile = false; // Stato della lista
    private String currentFiltroTesto = "";
    private String currentFiltroCosto = "TUTTI";

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) attivaPosizioneUtente();
                else Toast.makeText(requireContext(), "Permesso GPS necessario", Toast.LENGTH_SHORT).show();
            });

    public SearchFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI BINDING
        map = view.findViewById(R.id.map);
        txtCriteriAttivi = view.findViewById(R.id.txt_criteri_attivi);
        recyclerViewResults = view.findViewById(R.id.recycler_view_results);
        loadingContainer = view.findViewById(R.id.loading_container);
        layoutError = view.findViewById(R.id.layout_error_retry);
        txtErrorMsg = view.findViewById(R.id.txt_error_msg);
        btnRetry = view.findViewById(R.id.btn_retry);
        btnToggleList = view.findViewById(R.id.btn_toggle_list); // NUOVO

        // LISTA
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ParcheggioAdapter(new ArrayList<>(), this::onParcheggioClick);
        recyclerViewResults.setAdapter(adapter);
        recyclerViewResults.setVisibility(View.GONE);

        // MAPPA
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.5);

        // TOUCH OVERLAY
        Overlay touchOverlay = new Overlay() {
            @Override
            public boolean onDoubleTap(MotionEvent e, MapView mapView) {
                Projection proj = mapView.getProjection();
                GeoPoint loc = (GeoPoint) proj.fromPixels((int)e.getX(), (int)e.getY());
                mostraDialogNavigazioneCustom(loc);
                return true;
            }
        };
        map.getOverlays().add(touchOverlay);

        // GPS
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attivaPosizioneUtente();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // LISTENERS
        view.findViewById(R.id.btn_cerca_parcheggi).setOnClickListener(v -> mostrarDialogFiltri());

        // --- NUOVA LOGICA PULSANTE MOSTRA/NASCONDI LISTA ---
        btnToggleList.setOnClickListener(v -> {
            // Avvia un'animazione di transizione sulla card dei filtri
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) view.findViewById(R.id.card_search));

            if (isListaVisibile) {
                recyclerViewResults.setVisibility(View.GONE);
                btnToggleList.setText("Lista ⬇");
                isListaVisibile = false;
            } else {
                if (!adapter.isEmpty()) {
                    recyclerViewResults.setVisibility(View.VISIBLE);
                    btnToggleList.setText("Lista ⬆");
                    isListaVisibile = true;
                } else {
                    Toast.makeText(requireContext(), "Nessun risultato", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // --------------------------------------------------

        view.findViewById(R.id.fab_centra_posizione).setOnClickListener(v -> {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                GeoPoint myPos = myLocationOverlay.getMyLocation();
                map.getController().animateTo(myPos);
                myLocationOverlay.enableFollowLocation();
                checkDatiEVisualizza(myPos.getLatitude(), myPos.getLongitude(), true);
            } else {
                Toast.makeText(requireContext(), "Attendo GPS...", Toast.LENGTH_SHORT).show();
                attivaPosizioneUtente();
            }
        });

        btnRetry.setOnClickListener(v -> {
            GeoPoint center = (GeoPoint) map.getMapCenter();
            scaricaDatiParcheggi(center.getLatitude(), center.getLongitude());
        });

        view.findViewById(R.id.btn_back_home).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
    }

    private void attivaPosizioneUtente() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location lastKnown = null;
        try { lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception e) {}
        if (lastKnown == null) try { lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception e) {}

        if (lastKnown != null) {
            Log.d("PARKPIN_DEBUG", "Posizione trovata subito: " + lastKnown.getLatitude());
            GeoPoint startPoint = new GeoPoint(lastKnown.getLatitude(), lastKnown.getLongitude());
            map.getController().setCenter(startPoint);
            if (!isPrimoCaricamentoEffettuato) {
                checkDatiEVisualizza(startPoint.getLatitude(), startPoint.getLongitude(), false);
                isPrimoCaricamentoEffettuato = true;
            }
        } else {
            Log.d("PARKPIN_DEBUG", "GPS Freddo in Search. Richiedo aggiornamento...");
            loadingContainer.setVisibility(View.VISIBLE);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            } catch (Exception e) {}
        }

        myLocationOverlay.runOnFirstFix(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    GeoPoint myPos = myLocationOverlay.getMyLocation();
                    if (myPos != null && !isPrimoCaricamentoEffettuato) {
                        map.getController().animateTo(myPos);
                        checkDatiEVisualizza(myPos.getLatitude(), myPos.getLongitude(), false);
                        isPrimoCaricamentoEffettuato = true;
                    }
                });
            }
        });
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (locationManager != null) locationManager.removeUpdates(this);
        if (!isPrimoCaricamentoEffettuato) {
            map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
            checkDatiEVisualizza(location.getLatitude(), location.getLongitude(), false);
            isPrimoCaricamentoEffettuato = true;
        }
    }

    private void checkDatiEVisualizza(double lat, double lon, boolean fromButton) {
        boolean usaCache = false;
        if (ParkingCache.parcheggiSalvati != null && !ParkingCache.parcheggiSalvati.isEmpty() && ParkingCache.posizioneSalvataggio != null) {
            GeoPoint attuale = new GeoPoint(lat, lon);
            if (attuale.distanceToAsDouble(ParkingCache.posizioneSalvataggio) < 2000) usaCache = true;
        }

        if (usaCache) {
            loadingContainer.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
            tuttiParcheggiScaricati = ParkingCache.parcheggiSalvati;
            if (fromButton) Toast.makeText(requireContext(), "Uso dati in memoria ⚡", Toast.LENGTH_SHORT).show();
            visualizzaDati(currentFiltroTesto, currentFiltroCosto, true);
        } else {
            scaricaDatiParcheggi(lat, lon);
        }
    }

    private void scaricaDatiParcheggi(double lat, double lon) {
        loadingContainer.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);

        Log.d("PARKPIN_DEBUG", "🚀 Inizio download Search da: " + lat + ", " + lon);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "ParkPinApp/1.0").build()))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        OverpassService service = retrofit.create(OverpassService.class);

        String query = "[out:json][timeout:60];" +
                "nwr[\"amenity\"=\"parking\"](around:2000," + lat + "," + lon + ");" +
                "out tags center;";

        service.cercaParcheggi(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                loadingContainer.setVisibility(View.GONE);
                if (!isAdded() || map == null || map.getRepository() == null) {
                    return; // L'utente è uscito, fermiamo tutto!
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<OverpassResponse.Elemento> risultati = response.body().elementi;
                    if (risultati == null || risultati.isEmpty()) {
                        layoutError.setVisibility(View.VISIBLE);
                        txtErrorMsg.setText("Nessun parcheggio trovato in 2km.");
                        tuttiParcheggiScaricati = new ArrayList<>();
                    } else {
                        tuttiParcheggiScaricati = risultati;
                        ParkingCache.parcheggiSalvati = tuttiParcheggiScaricati;
                        ParkingCache.posizioneSalvataggio = new GeoPoint(lat, lon);

                        boolean mostraLista = !currentFiltroCosto.equals("TUTTI") || !currentFiltroTesto.isEmpty();
                        visualizzaDati(currentFiltroTesto, currentFiltroCosto, mostraLista);
                        Log.d("PARKPIN_DEBUG", "✅ SUCCESSO Search: " + tuttiParcheggiScaricati.size());
                        Toast.makeText(requireContext(), "Trovati: " + tuttiParcheggiScaricati.size(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    layoutError.setVisibility(View.VISIBLE);
                    txtErrorMsg.setText("Errore Server: " + response.code());
                    Log.e("PARKPIN_DEBUG", "⚠️ Errore Server Search: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                loadingContainer.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                txtErrorMsg.setText("Errore di connessione.");
                Log.e("PARKPIN_DEBUG", "❌ FALLITO Search: " + t.getMessage());
            }
        });
    }

    private void visualizzaDati(String testo, String costo, boolean mostraLista) {
        String label = "Filtri: " + costo;
        if(!testo.isEmpty()) label += " • " + testo;
        txtCriteriAttivi.setText(label);

        for (Marker m : markersAttuali) map.getOverlays().remove(m);
        markersAttuali.clear();

        List<OverpassResponse.Elemento> risultatiFiltrati = new ArrayList<>();
        if (tuttiParcheggiScaricati == null) return;

        int iconResId = R.drawable.baseline_local_parking_24;
        try { if (ContextCompat.getDrawable(requireContext(), iconResId) == null) iconResId = android.R.drawable.ic_menu_mapmode; }
        catch (Exception e) { iconResId = android.R.drawable.ic_menu_mapmode; }

        for (OverpassResponse.Elemento p : tuttiParcheggiScaricati) {
            if (p.lat == 0 && p.center != null) { p.lat = p.center.lat; p.lon = p.center.lon; }
            if (p.lat == 0 || p.lon == 0) continue;

            String nome = "Parcheggio";
            if (p.tags != null) {
                if (p.tags.name != null && !p.tags.name.isEmpty()) nome = p.tags.name;
                else if (p.tags.operator != null && !p.tags.operator.isEmpty()) nome = "Parcheggio " + p.tags.operator;
                else nome = "Parcheggio Pubblico";
            }

            // LOGICA STRADA
            String strada = (p.tags != null && p.tags.street != null) ? p.tags.street : "";

            boolean isPagamento = false;
            if (p.tags != null) {
                String fee = p.tags.fee;
                if (fee != null && !fee.equalsIgnoreCase("no")) isPagamento = true;
                if (fee != null && (fee.contains("pay") || fee.contains("ticket") || fee.contains("yes"))) isPagamento = true;
                if (p.tags.parking != null && (p.tags.parking.contains("garage") || p.tags.parking.contains("multi"))) {
                    if (fee == null || !fee.equalsIgnoreCase("no")) isPagamento = true;
                }
            }

            if (costo.equals("GRATIS") && isPagamento) continue;
            if (costo.equals("PAGAMENTO") && !isPagamento) continue;
            if (!testo.isEmpty()) {
                String s = testo.toLowerCase();
                if (!nome.toLowerCase().contains(s) && !strada.toLowerCase().contains(s)) continue;
            }

            risultatiFiltrati.add(p);

            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setTitle(nome);
            m.setSnippet(isPagamento ? "A Pagamento" : "Gratis");
            if (!strada.isEmpty()) m.setSubDescription(strada);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            Drawable icon = ContextCompat.getDrawable(requireContext(), iconResId);
            if (icon != null) {
                icon = icon.getConstantState().newDrawable().mutate();
                icon.setTint(isPagamento ? Color.parseColor("#D32F2F") : Color.parseColor("#388E3C"));
                m.setIcon(icon);
            }

            boolean finalIsPagamento = isPagamento;
            String finalNome = nome;
            m.setOnMarkerClickListener((marker, mapView) -> {
                confermaNavigazione(finalNome, marker.getPosition(), finalIsPagamento);
                return true;
            });

            map.getOverlays().add(0, m);
            markersAttuali.add(m);
        }
        map.invalidate();

        adapter.aggiornaDati(risultatiFiltrati);

        // Se la lista era aperta o i filtri richiedono visualizzazione, mostrala
        if (mostraLista && !risultatiFiltrati.isEmpty()) {
            recyclerViewResults.setVisibility(View.VISIBLE);
            btnToggleList.setText("Lista ⬆");
            isListaVisibile = true;
        } else {
            recyclerViewResults.setVisibility(View.GONE);
            btnToggleList.setText("Lista ⬇");
            isListaVisibile = false;
        }
    }

    private void mostrarDialogFiltri() {
        View view = getLayoutInflater().inflate(R.layout.dialog_search_filters, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(view).create();

        // Fondamentale per il tema scuro con angoli arrotondati
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        EditText etNome = view.findViewById(R.id.et_filtro_nome);
        RadioGroup rgCosto = view.findViewById(R.id.rg_costo);

        etNome.setText(currentFiltroTesto);
        if(currentFiltroCosto.equals("GRATIS")) rgCosto.check(R.id.rb_gratis);
        else if(currentFiltroCosto.equals("PAGAMENTO")) rgCosto.check(R.id.rb_pagamento);
        else rgCosto.check(R.id.rb_tutti);

        view.findViewById(R.id.btn_applica_filtri).setOnClickListener(v -> {
            currentFiltroTesto = etNome.getText().toString().trim();
            currentFiltroCosto = "TUTTI";
            int id = rgCosto.getCheckedRadioButtonId();
            if (id == R.id.rb_gratis) currentFiltroCosto = "GRATIS";
            else if (id == R.id.rb_pagamento) currentFiltroCosto = "PAGAMENTO";

            visualizzaDati(currentFiltroTesto, currentFiltroCosto, true);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void onParcheggioClick(OverpassResponse.Elemento p) {
        GeoPoint point = new GeoPoint(p.lat, p.lon);
        map.getController().animateTo(point);
        map.getController().setZoom(19.0);

        String nome = "Parcheggio";
        if (p.tags != null) {
            if (p.tags.name != null && !p.tags.name.isEmpty()) nome = p.tags.name;
            else if (p.tags.operator != null && !p.tags.operator.isEmpty()) nome = "Parcheggio " + p.tags.operator;
        }

        boolean isPagamento = p.tags != null && p.tags.fee != null && !p.tags.fee.equalsIgnoreCase("no");
        confermaNavigazione(nome, point, isPagamento);
    }

    private void confermaNavigazione(String nome, GeoPoint pos, boolean isPagamento) {
        // Infla il layout personalizzato
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_start_navigation, null);

        TextView txtAddress = dialogView.findViewById(R.id.txt_nav_address);
        txtAddress.setText(nome);

        // Crea il Dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();

        // Rende lo sfondo trasparente per vedere gli angoli tondi della Card
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM); // Appare dal basso
        }

        dialogView.findViewById(R.id.btn_nav_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_nav_confirm).setOnClickListener(v -> {
            avviaNavigazione(pos, nome, isPagamento);
            dialog.dismiss();
        });

        dialog.show();
    }

    // Aggiorna anche il double tap sulla mappa per usare lo stesso stile
    private void mostraDialogNavigazioneCustom(GeoPoint p) {
        confermaNavigazione("Punto Selezionato", p, false);
    }

    private void avviaNavigazione(GeoPoint dest, String nome, boolean isPaid) {
        Bundle bundle = new Bundle();
        bundle.putFloat("dest_lat", (float) dest.getLatitude());
        bundle.putFloat("dest_lon", (float) dest.getLongitude());
        bundle.putString("dest_nome", nome);
        bundle.putBoolean("dest_is_paid", isPaid);

        requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                .putBoolean("navigazione_attiva", true)
                .putFloat("dest_lat", (float) dest.getLatitude())
                .putFloat("dest_lon", (float) dest.getLongitude())
                .putString("dest_nome", nome)
                // Rimosso .putString("dest_nota", nota)
                .apply();

        NavHostFragment.findNavController(this).navigate(R.id.action_search_to_nav, bundle);
    }


    // --- ADAPTER AGGIORNATO CON STRADA ---
    private static class ParcheggioAdapter extends RecyclerView.Adapter<ParcheggioAdapter.ViewHolder> {
        private List<OverpassResponse.Elemento> list;
        private final OnItemClickListener listener;
        public interface OnItemClickListener { void onItemClick(OverpassResponse.Elemento item); }
        public ParcheggioAdapter(List<OverpassResponse.Elemento> list, OnItemClickListener listener) { this.list = list; this.listener = listener; }
        public void aggiornaDati(List<OverpassResponse.Elemento> nuoviDati) { this.list = nuoviDati; notifyDataSetChanged(); }
        public boolean isEmpty() { return list == null || list.isEmpty(); }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parking, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OverpassResponse.Elemento p = list.get(position);

            // Nome
            String nome = "Parcheggio";
            if (p.tags != null) {
                if (p.tags.name != null && !p.tags.name.isEmpty()) nome = p.tags.name;
                else if (p.tags.operator != null && !p.tags.operator.isEmpty()) nome = "Parcheggio " + p.tags.operator;
                else nome = "Parcheggio Pubblico";
            }

            // Strada
            String strada = (p.tags != null && p.tags.street != null) ? p.tags.street : "";

            boolean isPagamento = false;
            if (p.tags != null) {
                String fee = p.tags.fee;
                if (fee != null && !fee.equalsIgnoreCase("no")) isPagamento = true;
            }

            holder.txtName.setText(nome);

            // Info complete: Pagamento + Strada
            String info = isPagamento ? "💰 Pagamento" : "🆓 Gratis";
            if (!strada.isEmpty()) info += " • " + strada;
            holder.txtType.setText(info);

            holder.txtType.setTextColor(isPagamento ? Color.parseColor("#D32F2F") : Color.parseColor("#388E3C"));
            holder.imgIcon.setColorFilter(isPagamento ? Color.parseColor("#D32F2F") : Color.parseColor("#388E3C"));
            holder.itemView.setOnClickListener(v -> listener.onItemClick(p));
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtType; ImageView imgIcon;
            ViewHolder(View v) { super(v); txtName = v.findViewById(R.id.txt_park_name); txtType = v.findViewById(R.id.txt_park_type); imgIcon = v.findViewById(R.id.img_park_icon); }
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationManager != null) locationManager.removeUpdates(this);
    }
}