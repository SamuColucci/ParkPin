package com.example.parkpin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
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

import org.osmdroid.config.Configuration;
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

// --- IMPORT NECESSARI PER RISOLVERE ERRORE CONNESSIONE ---
import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.util.concurrent.TimeUnit;
// ---------------------------------------------------------

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    public interface OverpassService {
        @GET("interpreter")
        Call<OverpassResponse> cercaParcheggi(@Query("data") String data);
    }

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private ProgressBar loadingBar;
    private TextView txtCriteriAttivi;
    private RecyclerView recyclerViewResults;

    private List<OverpassResponse.Elemento> tuttiParcheggiScaricati = new ArrayList<>();
    private List<Marker> markersAttuali = new ArrayList<>();
    private ParcheggioAdapter adapter;

    private boolean isPrimoCaricamentoEffettuato = false;
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

        loadingBar = view.findViewById(R.id.loading_bar);
        txtCriteriAttivi = view.findViewById(R.id.txt_criteri_attivi);
        map = view.findViewById(R.id.map);
        recyclerViewResults = view.findViewById(R.id.recycler_view_results);

        // LISTA
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ParcheggioAdapter(new ArrayList<>(), this::onParcheggioClick);
        recyclerViewResults.setAdapter(adapter);
        recyclerViewResults.setVisibility(View.GONE);

        // MAPPA
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        // DOPPIO CLICK
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

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attivaPosizioneUtente();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // LISTENERS
        view.findViewById(R.id.btn_cerca_parcheggi).setOnClickListener(v -> mostrarDialogFiltri());

        view.findViewById(R.id.fab_centra_posizione).setOnClickListener(v -> {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                GeoPoint myPos = myLocationOverlay.getMyLocation();
                map.getController().animateTo(myPos);
                myLocationOverlay.enableFollowLocation();
                scaricaDatiParcheggi(myPos.getLatitude(), myPos.getLongitude());
            } else {
                Toast.makeText(requireContext(), "Ricerca GPS in corso...", Toast.LENGTH_SHORT).show();
                attivaPosizioneUtente();
            }
        });
    }

    private void attivaPosizioneUtente() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location lastKnown = null;
        try { lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception e) {}
        if (lastKnown == null) try { lastKnown = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception e) {}

        if (lastKnown != null) {
            GeoPoint startPoint = new GeoPoint(lastKnown.getLatitude(), lastKnown.getLongitude());
            map.getController().setCenter(startPoint);

            if (!isPrimoCaricamentoEffettuato) {
                scaricaDatiParcheggi(startPoint.getLatitude(), startPoint.getLongitude());
                isPrimoCaricamentoEffettuato = true;
            }
        }

        myLocationOverlay.runOnFirstFix(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    GeoPoint myPos = myLocationOverlay.getMyLocation();
                    if (myPos != null && !isPrimoCaricamentoEffettuato) {
                        map.getController().animateTo(myPos);
                        scaricaDatiParcheggi(myPos.getLatitude(), myPos.getLongitude());
                        isPrimoCaricamentoEffettuato = true;
                    }
                });
            }
        });
    }

    // --- METODO FIXATO PER LA CONNESSIONE ---
    // --- METODO AGGIORNATO: Server Kumi Systems (Molto Affidabile) ---
    private void scaricaDatiParcheggi(double lat, double lon) {
        if (lat == 0 || lon == 0) return;

        loadingBar.setVisibility(View.VISIBLE);

        // 1. Client HTTP Sicuro
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "ParkPinApp/1.0") // FONDAMENTALE
                            .build();
                    return chain.proceed(request);
                })
                .build();

        // 2. USO IL SERVER "KUMI SYSTEMS" (Il più stabile al mondo per Overpass)
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://overpass.kumi.systems/api/") // <--- URL CAMBIATO
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        OverpassService service = retrofit.create(OverpassService.class);

        // 3. Query (Raggio 1500m)
        String query = "[out:json][timeout:25];" +
                "nwr[\"amenity\"=\"parking\"](around:1500," + lat + "," + lon + ");" +
                "out tags center;";

        service.cercaParcheggi(query).enqueue(new Callback<OverpassResponse>() {
            @Override
            public void onResponse(@NonNull Call<OverpassResponse> call, @NonNull Response<OverpassResponse> response) {
                loadingBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    tuttiParcheggiScaricati = response.body().elementi;
                    if(tuttiParcheggiScaricati == null) tuttiParcheggiScaricati = new ArrayList<>();

                    boolean mostraLista = !currentFiltroCosto.equals("TUTTI") || !currentFiltroTesto.isEmpty();
                    visualizzaDati(currentFiltroTesto, currentFiltroCosto, mostraLista);

                    if (!tuttiParcheggiScaricati.isEmpty()) {
                        Toast.makeText(requireContext(), "Trovati " + tuttiParcheggiScaricati.size() + " parcheggi!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Nessun parcheggio in zona (1.5km).", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Errore specifico del server
                    String msg = "Errore Server: " + response.code();
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    Log.e("ParkPin", "Errore Body: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(@NonNull Call<OverpassResponse> call, @NonNull Throwable t) {
                loadingBar.setVisibility(View.GONE);

                // DIAGNOSTICA: Capiamo perché fallisce
                String errorMsg = t.getMessage();
                if (errorMsg != null && errorMsg.contains("Unable to resolve host")) {
                    Toast.makeText(requireContext(), "Nessuna connessione Internet!", Toast.LENGTH_LONG).show();
                } else if (errorMsg != null && errorMsg.contains("timeout")) {
                    Toast.makeText(requireContext(), "Connessione lenta (Timeout).", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "Errore rete generico.", Toast.LENGTH_SHORT).show();
                }
                Log.e("ParkPin", "Errore Rete Dettagliato: ", t);
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

            String nome = (p.tags != null && p.tags.nome != null) ? p.tags.nome : "Parcheggio";
            String strada = (p.tags != null && p.tags.strada != null) ? p.tags.strada : "";

            boolean isPagamento = false;
            if (p.tags != null) {
                String fee = p.tags.fee;
                if (fee != null && !fee.equalsIgnoreCase("no")) isPagamento = true;
                if (fee != null && (fee.contains("pay") || fee.contains("yes") || fee.contains("ticket"))) isPagamento = true;
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
            m.setSubDescription(strada);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            Drawable icon = ContextCompat.getDrawable(requireContext(), iconResId);
            if (icon != null) {
                icon = icon.getConstantState().newDrawable().mutate();
                icon.setTint(isPagamento ? Color.parseColor("#D32F2F") : Color.parseColor("#388E3C"));
                m.setIcon(icon);
            }

            boolean finalIsPagamento = isPagamento;
            m.setOnMarkerClickListener((marker, mapView) -> {
                confermaNavigazione(nome, marker.getPosition(), finalIsPagamento);
                return true;
            });

            map.getOverlays().add(0, m);
            markersAttuali.add(m);
        }
        map.invalidate();

        adapter.aggiornaDati(risultatiFiltrati);
        if (mostraLista && !risultatiFiltrati.isEmpty()) {
            recyclerViewResults.setVisibility(View.VISIBLE);
        } else {
            recyclerViewResults.setVisibility(View.GONE);
        }
    }

    private void mostrarDialogFiltri() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_search_filters, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

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

            GeoPoint center = (GeoPoint) map.getMapCenter();
            if(center.getLatitude() != 0) scaricaDatiParcheggi(center.getLatitude(), center.getLongitude());

            dialog.dismiss();
        });
        dialog.show();
    }

    private void onParcheggioClick(OverpassResponse.Elemento p) {
        GeoPoint point = new GeoPoint(p.lat, p.lon);
        map.getController().animateTo(point);
        map.getController().setZoom(19.0);
        String nome = (p.tags != null && p.tags.nome != null) ? p.tags.nome : "Parcheggio";
        boolean isPagamento = false;
        if (p.tags != null && p.tags.fee != null && !p.tags.fee.equalsIgnoreCase("no")) isPagamento = true;
        confermaNavigazione(nome, point, isPagamento);
    }

    private void confermaNavigazione(String nome, GeoPoint pos, boolean isPagamento) {
        new AlertDialog.Builder(requireContext())
                .setTitle(nome)
                .setMessage("Avviare navigazione?")
                .setPositiveButton("VAI 🚗", (d, w) -> avviaNavigazione(pos, nome, isPagamento))
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void mostraDialogNavigazioneCustom(GeoPoint p) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Destinazione Custom")
                .setMessage("Navigare verso questo punto?")
                .setPositiveButton("SÌ", (d, w) -> {
                    Marker m = new Marker(map); m.setPosition(p); map.getOverlays().add(m); map.invalidate();
                    avviaNavigazione(p, "Punto Selezionato", false);
                }).setNegativeButton("No", null).show();
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
                .putString("dest_nome", nome).apply();
        NavHostFragment.findNavController(this).navigate(R.id.action_search_to_nav, bundle);
    }

    private static class ParcheggioAdapter extends RecyclerView.Adapter<ParcheggioAdapter.ViewHolder> {
        private List<OverpassResponse.Elemento> list;
        private final OnItemClickListener listener;
        public interface OnItemClickListener { void onItemClick(OverpassResponse.Elemento item); }
        public ParcheggioAdapter(List<OverpassResponse.Elemento> list, OnItemClickListener listener) { this.list = list; this.listener = listener; }
        public void aggiornaDati(List<OverpassResponse.Elemento> nuoviDati) { this.list = nuoviDati; notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_parking, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OverpassResponse.Elemento p = list.get(position);
            String nome = (p.tags != null && p.tags.nome != null) ? p.tags.nome : "Parcheggio";

            boolean isPagamento = false;
            if (p.tags != null) {
                String fee = p.tags.fee;
                if (fee != null && !fee.equalsIgnoreCase("no")) isPagamento = true;
                if (fee != null && (fee.contains("pay") || fee.contains("ticket"))) isPagamento = true;
            }

            holder.txtName.setText(nome);
            holder.txtType.setText(isPagamento ? "💰 Pagamento" : "🆓 Gratis");
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

    public static android.graphics.Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override public void onResume() { super.onResume(); if(map!=null) map.onResume(); }
    @Override public void onPause() { super.onPause(); if(map!=null) map.onPause(); }
}