package com.example.parkpin;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener; // IMPORTANTE
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log; // IMPORTANTE PER I LOG
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import com.example.parkpin.data.OverpassResponse;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SaveCarFragment extends Fragment implements LocationListener { // AGGIUNTO LocationListener

    public interface OverpassService {
        @GET("interpreter")
        Call<OverpassResponse> cercaParcheggi(@Query("data") String data);
    }

    private MapView map;
    private TextView txtIndirizzo;
    private TextInputEditText etNote;
    private TextView txtTimerStatus;

    private LinearLayout loadingContainer;
    private LinearLayout layoutError;
    private TextView txtErrorMsg;
    private Button btnRetry;

    private Marker markerPosizioneAuto;
    private double latSelezionata = 0;
    private double lonSelezionata = 0;
    private LocationManager locationManager; // Per gestire il GPS

    private List<Marker> parcheggiMarkers = new ArrayList<>();

    public SaveCarFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_save_car, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI BINDING
        map = view.findViewById(R.id.map_save);
        txtIndirizzo = view.findViewById(R.id.txt_indirizzo_dinamico);
        etNote = view.findViewById(R.id.et_note_save);
        txtTimerStatus = view.findViewById(R.id.txt_timer_status_save);
        loadingContainer = view.findViewById(R.id.loading_container);
        layoutError = view.findViewById(R.id.layout_error_retry);
        txtErrorMsg = view.findViewById(R.id.txt_error_msg);
        btnRetry = view.findViewById(R.id.btn_retry);

        // MAPPA
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(19.0);

        // MARKER
        markerPosizioneAuto = new Marker(map);
        markerPosizioneAuto.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        markerPosizioneAuto.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.baseline_directions_car_24));
        markerPosizioneAuto.setTitle("La tua Auto");
        markerPosizioneAuto.setDraggable(true);
        markerPosizioneAuto.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker marker) {}
            @Override public void onMarkerDragStart(Marker marker) {}
            @Override
            public void onMarkerDragEnd(Marker marker) {
                aggiornaPosizioneScelta(marker.getPosition());
            }
        });
        map.getOverlays().add(markerPosizioneAuto);

        // OVERLAY
        Overlay touchOverlay = new Overlay() {
            @Override
            public boolean onDoubleTap(MotionEvent e, MapView mapView) {
                Projection proj = mapView.getProjection();
                GeoPoint loc = (GeoPoint) proj.fromPixels((int)e.getX(), (int)e.getY());
                aggiornaPosizioneScelta(loc);
                return true;
            }
        };
        map.getOverlays().add(touchOverlay);

        // BOTTONI ZOOM
        view.findViewById(R.id.btn_zoom_in).setOnClickListener(v -> map.getController().zoomIn());
        view.findViewById(R.id.btn_zoom_out).setOnClickListener(v -> map.getController().zoomOut());

        // LOGICA AVVIO
        if (getArguments() != null && getArguments().containsKey("lat_arrivo")) {
            double lat = getArguments().getFloat("lat_arrivo");
            double lon = getArguments().getFloat("lon_arrivo");
            boolean isPaid = getArguments().getBoolean("is_paid", false);

            GeoPoint p = new GeoPoint(lat, lon);
            aggiornaPosizioneScelta(p);
            map.getController().setCenter(p);

            if (isPaid) mostraDialogTimer();
            caricaParcheggiVicini(lat, lon);
        } else {
            // Se non arrivo dalla navigazione, cerco la mia posizione
            cercaPosizioneGPSIniziale();
        }

        // LISTENERS
        view.findViewById(R.id.fab_my_pos_save).setOnClickListener(v -> cercaPosizioneGPSIniziale());
        view.findViewById(R.id.btn_timer_save).setOnClickListener(v -> mostraDialogTimer());
        view.findViewById(R.id.btn_confirm_save).setOnClickListener(v -> salvaPosizioneDefinitiva());

        btnRetry.setOnClickListener(v -> caricaParcheggiVicini(latSelezionata, lonSelezionata));

        view.findViewById(R.id.btn_back_home).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
    }

    private void aggiornaPosizioneScelta(GeoPoint p) {
        latSelezionata = p.getLatitude();
        lonSelezionata = p.getLongitude();
        markerPosizioneAuto.setPosition(p);
        map.invalidate();

        new Thread(() -> {
            String testo = String.format("%.5f, %.5f", latSelezionata, lonSelezionata);
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latSelezionata, lonSelezionata, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address obj = addresses.get(0);
                    if (obj.getThoroughfare() != null) {
                        testo = obj.getThoroughfare();
                        if (obj.getSubThoroughfare() != null) testo += ", " + obj.getSubThoroughfare();
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
            String finale = testo;
            requireActivity().runOnUiThread(() -> txtIndirizzo.setText(finale));
        }).start();
    }

    // --- LOGICA POSIZIONE CORRETTA ---
    private void cercaPosizioneGPSIniziale() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        try { loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch(Exception e){}
        if(loc == null) try { loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch(Exception e){}

        if (loc != null) {
            // Posizione trovata subito!
            Log.d("PARKPIN_DEBUG", "Posizione trovata subito: " + loc.getLatitude());
            usarePosizioneTrovata(loc);
        } else {
            // Posizione non trovata (GPS Freddo). Chiediamo aggiornamento!
            Log.d("PARKPIN_DEBUG", "GPS Freddo. Richiedo aggiornamento...");
            Toast.makeText(requireContext(), "Cerco segnale GPS...", Toast.LENGTH_SHORT).show();
            loadingContainer.setVisibility(View.VISIBLE); // Mostra caricamento mentre cerca GPS
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d("PARKPIN_DEBUG", "Posizione aggiornata dal Listener!");
        if (locationManager != null) locationManager.removeUpdates(this); // Ferma GPS
        usarePosizioneTrovata(location);
    }

    private void usarePosizioneTrovata(Location loc) {
        GeoPoint myPos = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        map.getController().animateTo(myPos);
        aggiornaPosizioneScelta(myPos);
        caricaParcheggiVicini(loc.getLatitude(), loc.getLongitude());
    }

    // --- CARICAMENTO CON CAMBIO SERVER E LOG ---
    private void caricaParcheggiVicini(double lat, double lon) {
        loadingContainer.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);

        Log.d("PARKPIN_DEBUG", "🚀 Inizio download parcheggi da: " + lat + ", " + lon);

        // Controllo Cache
        if (ParkingCache.parcheggiSalvati != null && !ParkingCache.parcheggiSalvati.isEmpty()) {
            // Controllo distanza (opzionale, qui semplice)
            loadingContainer.setVisibility(View.GONE);
            disegnaParcheggiSullaMappa(ParkingCache.parcheggiSalvati);
            Log.d("PARKPIN_DEBUG", "♻️ Dati dalla Cache.");
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "ParkPinApp/1.0").build()))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://overpass-api.de/api/") // <--- SERVER PRINCIPALE (Più affidabile)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        String query = "[out:json][timeout:25];" +
                "nwr[\"amenity\"=\"parking\"](around:2000," + lat + "," + lon + ");" +
                "out tags center;";

        retrofit.create(OverpassService.class).cercaParcheggi(query).enqueue(new retrofit2.Callback<OverpassResponse>() {
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
                        txtErrorMsg.setText("Nessun parcheggio qui vicino.");
                        Log.d("PARKPIN_DEBUG", "⚠️ Download OK ma 0 parcheggi.");
                    } else {
                        ParkingCache.parcheggiSalvati = risultati;
                        ParkingCache.posizioneSalvataggio = new GeoPoint(lat, lon);
                        disegnaParcheggiSullaMappa(risultati);
                        Log.d("PARKPIN_DEBUG", "✅ SUCCESSO: " + risultati.size() + " parcheggi.");
                    }
                } else {
                    layoutError.setVisibility(View.VISIBLE);
                    txtErrorMsg.setText("Errore Server: " + response.code());
                    Log.e("PARKPIN_DEBUG", "❌ Errore Server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                loadingContainer.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                txtErrorMsg.setText("Errore di connessione.");
                Log.e("PARKPIN_DEBUG", "❌ FALLITO: " + t.getMessage());
            }
        });
    }

    private void disegnaParcheggiSullaMappa(List<OverpassResponse.Elemento> lista) {
        if (lista == null || map == null) return;

        for (Marker m : parcheggiMarkers) {
            map.getOverlays().remove(m);
        }
        parcheggiMarkers.clear();

        int iconResId = R.drawable.baseline_local_parking_24;
        try { if (ContextCompat.getDrawable(requireContext(), iconResId) == null) iconResId = android.R.drawable.ic_menu_mapmode; }
        catch (Exception e) { iconResId = android.R.drawable.ic_menu_mapmode; }

        for (OverpassResponse.Elemento p : lista) {
            if (p.lat == 0 && p.center != null) { p.lat = p.center.lat; p.lon = p.center.lon; }
            if (p.lat == 0 || p.lon == 0) continue;

            boolean isPagamento = false;
            if (p.tags != null) {
                String fee = p.tags.fee;
                if (fee != null && (fee.contains("yes") || fee.contains("pay") || fee.contains("ticket"))) isPagamento = true;
            }

            String nomeReale = (p.tags != null && p.tags.name != null) ? p.tags.name : "Parcheggio";

            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setTitle(nomeReale);
            m.setSnippet(isPagamento ? "A Pagamento" : "Gratis");

            Drawable icon = ContextCompat.getDrawable(requireContext(), iconResId);
            if (icon != null) {
                icon = icon.getConstantState().newDrawable().mutate();
                icon.setTint(isPagamento ? Color.parseColor("#D32F2F") : Color.parseColor("#388E3C"));
                m.setIcon(icon);
            }
            m.setDraggable(false);

            final String finalNome = nomeReale;
            final boolean finalIsPagamento = isPagamento;

            m.setOnMarkerClickListener((marker, mapView) -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Salvare qui?")
                        .setMessage("Vuoi impostare '" + finalNome + "' come posizione auto?")
                        .setPositiveButton("SÌ", (dialog, which) -> {
                            GeoPoint posParcheggio = marker.getPosition();
                            aggiornaPosizioneScelta(posParcheggio);
                            map.getController().animateTo(posParcheggio);
                            Toast.makeText(requireContext(), "Posizione aggiornata!", Toast.LENGTH_SHORT).show();
                            if (finalIsPagamento) etNote.setText(etNote.getText() + " (A Pagamento)");
                        })
                        .setNegativeButton("No", null).show();
                return true;
            });

            map.getOverlays().add(0, m);
            parcheggiMarkers.add(m);
        }
        map.invalidate();
    }

    private void salvaPosizioneDefinitiva() {
        if (latSelezionata == 0 || lonSelezionata == 0) {
            Toast.makeText(requireContext(), "Posizione non valida!", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("auto_salvata", true).putFloat("car_lat", (float) latSelezionata)
                .putFloat("car_lon", (float) lonSelezionata).putString("note_auto", etNote.getText().toString())
                .putBoolean("navigazione_attiva", false).apply();
        Toast.makeText(requireContext(), "✅ Posizione Salvata!", Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(this).navigate(R.id.action_save_to_home);
    }

    private void mostraDialogTimer() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> schedulaNotifica(hourOfDay, minute),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void schedulaNotifica(int oraScadenza, int minutiScadenza) {
        Calendar calendarScadenza = Calendar.getInstance();
        calendarScadenza.set(Calendar.HOUR_OF_DAY, oraScadenza);
        calendarScadenza.set(Calendar.MINUTE, minutiScadenza);
        calendarScadenza.set(Calendar.SECOND, 0);
        if (calendarScadenza.before(Calendar.getInstance())) calendarScadenza.add(Calendar.DAY_OF_MONTH, 1);
        long tempoNotifica = calendarScadenza.getTimeInMillis() - (10 * 60 * 1000);
        if (tempoNotifica <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), "Troppo tardi!", Toast.LENGTH_SHORT).show();
            return;
        }
        AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), ParkingAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoNotifica, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoNotifica, pi);
        }
        String orario = String.format("%02d:%02d", oraScadenza, minutiScadenza);
        txtTimerStatus.setText("Scadenza: " + orario);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override public void onDestroyView() {
        super.onDestroyView();
        if(locationManager != null) locationManager.removeUpdates(this);
    }

    @Override public void onResume() { super.onResume(); if(map!=null) map.onResume(); }
    @Override public void onPause() { super.onPause(); if(map!=null) map.onPause(); }
}