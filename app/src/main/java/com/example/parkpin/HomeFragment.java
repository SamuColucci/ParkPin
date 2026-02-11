package com.example.parkpin;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class HomeFragment extends Fragment {

    private LinearLayout layoutNormal, layoutNav, layoutRitorno;
    private View containerCenter, containerButtons;
    private TextView sottotitolo;

    private MapView mapPreviewSaved;
    private MapView mapPreviewCurrent;
    private MyLocationNewOverlay myLocationOverlay;

    public HomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI Setup
        View cardLogo = view.findViewById(R.id.card_logo);
        View titolo = view.findViewById(R.id.txt_titolo);
        sottotitolo = view.findViewById(R.id.txt_sottotitolo);
        containerCenter = view.findViewById(R.id.container_center);
        containerButtons = view.findViewById(R.id.container_buttons);
        layoutNormal = view.findViewById(R.id.layout_normal);
        layoutNav = view.findViewById(R.id.layout_navigazione);
        layoutRitorno = view.findViewById(R.id.layout_ritorno_auto);

        mapPreviewSaved = view.findViewById(R.id.map_preview_home);
        mapPreviewCurrent = view.findViewById(R.id.map_preview_current);

        aggiornaStatoUI();

        // Listeners
        view.findViewById(R.id.btn_mappa).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.action_home_to_search));
        view.findViewById(R.id.btn_salva_posizione).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.action_home_to_save));
        view.findViewById(R.id.btn_continua_guida).setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav));
        view.findViewById(R.id.btn_ritorna_auto).setOnClickListener(v -> avviaNavigazioneVersoAuto());
        view.findViewById(R.id.btn_elimina_auto).setOnClickListener(v -> {
            requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                    .remove("auto_salvata").remove("car_lat").remove("car_lon").remove("note_auto").apply();
            aggiornaStatoUI();
        });
    }

    private void aggiornaStatoUI() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        boolean isNavigazioneAttiva = prefs.getBoolean("navigazione_attiva", false);
        boolean isAutoSalvata = prefs.getBoolean("auto_salvata", false);

        layoutNormal.setVisibility(View.GONE);
        layoutNav.setVisibility(View.GONE);
        layoutRitorno.setVisibility(View.GONE);

        if (isNavigazioneAttiva) {
            layoutNav.setVisibility(View.VISIBLE);
            sottotitolo.setText("Navigazione in corso...");
        } else if (isAutoSalvata) {
            layoutRitorno.setVisibility(View.VISIBLE);
            sottotitolo.setText("La tua auto è al sicuro.");
            float lat = prefs.getFloat("car_lat", 0);
            float lon = prefs.getFloat("car_lon", 0);
            if (lat != 0) mostraMappaSaved(lat, lon);
        } else {
            layoutNormal.setVisibility(View.VISIBLE);
            sottotitolo.setText("Trova. Parcheggia. Ritrova.");
            mostraMappaPosizioneAttuale();
        }
    }

    private void mostraMappaSaved(double lat, double lon) {
        if (mapPreviewSaved == null) return;
        mapPreviewSaved.setTileSource(TileSourceFactory.MAPNIK);
        mapPreviewSaved.setMultiTouchControls(false);
        mapPreviewSaved.getController().setZoom(17.5);
        GeoPoint p = new GeoPoint(lat, lon);
        mapPreviewSaved.getController().setCenter(p);
        mapPreviewSaved.getOverlays().clear();
        Marker m = new Marker(mapPreviewSaved);
        m.setPosition(p);
        try {
            Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_directions_car_24);
            if(icon!=null){ icon.setTint(Color.RED); m.setIcon(icon); }
        } catch(Exception e){}
        mapPreviewSaved.getOverlays().add(m);
        mapPreviewSaved.invalidate();
    }

    private void mostraMappaPosizioneAttuale() {
        if (mapPreviewCurrent == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        mapPreviewCurrent.setTileSource(TileSourceFactory.MAPNIK);
        mapPreviewCurrent.setMultiTouchControls(false);
        mapPreviewCurrent.getController().setZoom(17.0);

        // Se torniamo indietro e l'overlay esiste già, lo rimuoviamo per ricrearlo pulito
        if (myLocationOverlay != null) {
            mapPreviewCurrent.getOverlays().remove(myLocationOverlay);
            myLocationOverlay.disableMyLocation();
            myLocationOverlay = null;
        }

        // Creazione pulita dell'overlay
        GpsMyLocationProvider provider = new GpsMyLocationProvider(requireContext());
        myLocationOverlay = new MyLocationNewOverlay(provider, mapPreviewCurrent);
        myLocationOverlay.enableMyLocation();
        mapPreviewCurrent.getOverlays().add(myLocationOverlay);

        myLocationOverlay.runOnFirstFix(() -> {
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (mapPreviewCurrent != null && myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                        mapPreviewCurrent.getController().animateTo(myLocationOverlay.getMyLocation());
                    }
                });
            }
        });

        mapPreviewCurrent.invalidate(); // Forza il ridisegno della mappa per togliere il celeste
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mapPreviewSaved != null) mapPreviewSaved.onResume();
        if(mapPreviewCurrent != null) mapPreviewCurrent.onResume();

        // Se siamo nello stato normale (senza auto), forziamo il refresh della mappa attuale
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_salvata", false) && !prefs.getBoolean("navigazione_attiva", false)) {
            mostraMappaPosizioneAttuale();
        }
    }

    private void avviaNavigazioneVersoAuto() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        float lat = prefs.getFloat("car_lat", 0);
        float lon = prefs.getFloat("car_lon", 0);
        if (lat == 0) return;
        Bundle b = new Bundle();
        b.putFloat("dest_lat", lat);
        b.putFloat("dest_lon", lon);
        b.putString("dest_nome", "La tua Auto");
        prefs.edit().putBoolean("navigazione_attiva", true).putFloat("dest_lat", lat).putFloat("dest_lon", lon).apply();
        NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav, b);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mapPreviewSaved != null) mapPreviewSaved.onPause();
        if(mapPreviewCurrent != null) mapPreviewCurrent.onPause();
        if(myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }
}