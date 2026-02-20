package com.example.parkpin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TimePicker;

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
import java.util.List;
import java.util.Locale;

/**
  - Mostrare la mappa per permettere all'utente di posizionare il "pin" dell'auto
  trascinandolo o facendo doppio tap.
  - Scaricare e mostrare i parcheggi nelle vicinanze, permettendo all'utente di selezionarne
  uno preciso.
  - Salvare le coordinate finali e le note utente nelle SharedPreferences per ritrovare l'auto in seguito.
 */

public class SaveCarFragment extends Fragment implements LocationListener {

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
    private LocationManager locationManager;
    private boolean isPosizioneInizialeImpostata = false;

    private List<Marker> parcheggiMarkers = new ArrayList<>();

    public SaveCarFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_save_car, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        map = view.findViewById(R.id.map_save);
        txtIndirizzo = view.findViewById(R.id.txt_indirizzo_dinamico);
        etNote = view.findViewById(R.id.et_note_save);
        txtTimerStatus = view.findViewById(R.id.txt_timer_status_save);
        loadingContainer = view.findViewById(R.id.loading_container);
        layoutError = view.findViewById(R.id.layout_error_retry);
        txtErrorMsg = view.findViewById(R.id.txt_error_msg);
        btnRetry = view.findViewById(R.id.btn_retry);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(19.0);

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

        view.findViewById(R.id.btn_zoom_in).setOnClickListener(v -> map.getController().zoomIn());
        view.findViewById(R.id.btn_zoom_out).setOnClickListener(v -> map.getController().zoomOut());

        if (getArguments() != null && getArguments().containsKey("lat_arrivo")) {
            double lat = getArguments().getFloat("lat_arrivo");
            double lon = getArguments().getFloat("lon_arrivo");
            boolean isPaid = getArguments().getBoolean("is_paid", false);

            GeoPoint p = new GeoPoint(lat, lon);
            isPosizioneInizialeImpostata = true;

            aggiornaPosizioneScelta(p);
            map.getController().setCenter(p);

            if (isPaid) {
                mostraDialogTimer();
            }
            caricaParcheggiVicini(lat, lon);
        } else {
            isPosizioneInizialeImpostata = false;
            cercaPosizioneGPSIniziale();
        }

        view.findViewById(R.id.fab_my_pos_save).setOnClickListener(v -> {
            isPosizioneInizialeImpostata = false;
            cercaPosizioneGPSIniziale();
        });

        view.findViewById(R.id.btn_timer_save).setOnClickListener(v -> mostraDialogTimer());
        view.findViewById(R.id.btn_confirm_save).setOnClickListener(v -> salvaPosizioneDefinitiva());

        btnRetry.setOnClickListener(v -> caricaParcheggiVicini(latSelezionata, lonSelezionata));

        view.findViewById(R.id.btn_back_home).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        View bottomSheet = view.findViewById(R.id.card_bottom_save);
        com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
        behavior.setHideable(false);
        behavior.setPeekHeight(450);
        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
    }

    private void aggiornaPosizioneScelta(GeoPoint p) {
        latSelezionata = p.getLatitude();
        lonSelezionata = p.getLongitude();
        markerPosizioneAuto.setPosition(p);
        map.invalidate();

        //Thread per trasformare un indirizzo in coordinate in uno leggibile
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
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (txtIndirizzo != null) {
                        txtIndirizzo.setText(finale);
                    }
                });
            }
        }).start();
    }

    private void cercaPosizioneGPSIniziale() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location loc = null;
        try { loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch(Exception e){}
        if(loc == null) try { loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch(Exception e){}

        if (loc != null) {
            usarePosizioneTrovata(loc);
        } else {
            loadingContainer.setVisibility(View.VISIBLE);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (locationManager != null) locationManager.removeUpdates(this);
        if (!isPosizioneInizialeImpostata) {
            usarePosizioneTrovata(location);
        }
    }

    private void usarePosizioneTrovata(Location loc) {
        GeoPoint myPos = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        map.getController().animateTo(myPos);
        aggiornaPosizioneScelta(myPos);
        isPosizioneInizialeImpostata = true;
        caricaParcheggiVicini(loc.getLatitude(), loc.getLongitude());
    }

    private void caricaParcheggiVicini(double lat, double lon) {
        loadingContainer.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);

        if (ParkingCache.parcheggiSalvati != null && !ParkingCache.parcheggiSalvati.isEmpty()) {
            loadingContainer.setVisibility(View.GONE);
            disegnaParcheggiSullaMappa(ParkingCache.parcheggiSalvati);
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://overpass.kumi.systems/api/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        String query = "[out:json][timeout:60];" +
                "nwr[\"amenity\"=\"parking\"](around:2000," + lat + "," + lon + ");" +
                "out tags center;";

        retrofit.create(OverpassService.class).cercaParcheggi(query).enqueue(new retrofit2.Callback<OverpassResponse>() {
            @Override
            public void onResponse(Call<OverpassResponse> call, Response<OverpassResponse> response) {
                loadingContainer.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<OverpassResponse.Elemento> risultati = response.body().elementi;
                    if (risultati != null && !risultati.isEmpty()) {
                        ParkingCache.parcheggiSalvati = risultati;
                        ParkingCache.posizioneSalvataggio = new GeoPoint(lat, lon);
                        disegnaParcheggiSullaMappa(risultati);
                    } else {
                        layoutError.setVisibility(View.VISIBLE);
                        txtErrorMsg.setText("Nessun parcheggio trovato.");
                    }
                } else {
                    layoutError.setVisibility(View.VISIBLE);
                    txtErrorMsg.setText("Errore Server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<OverpassResponse> call, Throwable t) {
                loadingContainer.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                txtErrorMsg.setText("Errore di connessione.");
            }
        });
    }

    private void disegnaParcheggiSullaMappa(List<OverpassResponse.Elemento> lista) {
        if (lista == null || map == null || getContext() == null) return;

        for (Marker m : parcheggiMarkers) map.getOverlays().remove(m);
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
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_stop_navigation, null);
                TextView title = dialogView.findViewById(R.id.txt_nav_title);
                TextView msg = dialogView.findViewById(R.id.txt_nav_address);
                com.google.android.material.button.MaterialButton btnAnnulla = dialogView.findViewById(R.id.btn_keep_nav);
                com.google.android.material.button.MaterialButton btnConferma = dialogView.findViewById(R.id.btn_confirm_stop);

                if(title != null) title.setText("Salvare qui?");
                if(msg != null) msg.setText("Vuoi impostare '" + finalNome + "' come posizione auto?");
                if(btnAnnulla != null) btnAnnulla.setText("Annulla");
                if(btnConferma != null) btnConferma.setText("SÌ, SALVA");

                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                }

                dialogView.findViewById(R.id.btn_keep_nav).setOnClickListener(v -> dialog.dismiss());
                dialogView.findViewById(R.id.btn_confirm_stop).setOnClickListener(v -> {
                    GeoPoint posParcheggio = marker.getPosition();
                    aggiornaPosizioneScelta(posParcheggio);
                    map.getController().animateTo(posParcheggio);
                    Toast.makeText(requireContext(), "Posizione aggiornata!", Toast.LENGTH_SHORT).show();

                    if (finalIsPagamento) etNote.setText("(A Pagamento)");
                    else etNote.setText("");

                    dialog.dismiss();

                    if (finalIsPagamento) {
                        mostraDialogTimer();
                    }
                });

                dialog.show();
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
        prefs.edit().putBoolean("auto_salvata", true)
                .putFloat("car_lat", (float) latSelezionata)
                .putFloat("car_lon", (float) lonSelezionata)
                .putString("note_auto", etNote.getText().toString())
                .putBoolean("navigazione_attiva", false).apply();
        Toast.makeText(requireContext(), "✅ Posizione Salvata!", Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(this).navigate(R.id.action_save_to_home);
    }

    private void mostraDialogTimer() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_timer_custom, null);

        TimePicker tp = dialogView.findViewById(R.id.custom_time_picker);
        tp.setIs24HourView(true);
        if(tp.getChildCount() > 0) tp.getChildAt(0).setBackgroundColor(Color.TRANSPARENT);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        View btnElimina = dialogView.findViewById(R.id.btn_elimina_timer);
        if (btnElimina != null) {
            btnElimina.setOnClickListener(v -> {
                dialog.dismiss();
                mostraConfermaRimozioneTimer();
            });
        }

        dialogView.findViewById(R.id.btn_annulla_timer).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_conferma_timer).setOnClickListener(v -> {
            int hour, minute;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                hour = tp.getHour();
                minute = tp.getMinute();
            } else {
                hour = tp.getCurrentHour();
                minute = tp.getCurrentMinute();
            }

            schedulaNotifica(hour, minute);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void mostraConfermaRimozioneTimer() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_confirmation, null);

        AlertDialog confirmDialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_annulla_delete).setOnClickListener(v -> confirmDialog.dismiss());

        view.findViewById(R.id.btn_conferma_delete).setOnClickListener(v -> {
            NotificationHelper.cancellaAvviso(requireContext());
            txtTimerStatus.setText("");
            Toast.makeText(requireContext(), "Timer rimosso correttamente", Toast.LENGTH_SHORT).show();
            confirmDialog.dismiss();
        });

        confirmDialog.show();
    }

    private void schedulaNotifica(int oraScadenza, int minutiScadenza) {
        NotificationHelper.prenotaAvviso(requireContext(), oraScadenza, minutiScadenza);
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