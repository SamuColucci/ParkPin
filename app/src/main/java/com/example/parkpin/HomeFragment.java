package com.example.parkpin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent; // Importante per bloccare tocco mappa
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

// OSMDroid Imports
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class HomeFragment extends Fragment {

    // Variabili UI
    private LinearLayout layoutNormal;
    private LinearLayout layoutNav;
    private LinearLayout layoutRitorno;
    private View containerCenter;
    private View containerButtons;
    private TextView sottotitolo;

    // Mappa Anteprima
    private MapView mapPreview;
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

        // --- 1. RIFERIMENTI UI ---
        View cardLogo = view.findViewById(R.id.card_logo);
        View titolo = view.findViewById(R.id.txt_titolo);
        sottotitolo = view.findViewById(R.id.txt_sottotitolo);

        containerCenter = view.findViewById(R.id.container_center);
        containerButtons = view.findViewById(R.id.container_buttons);

        layoutNormal = view.findViewById(R.id.layout_normal);
        layoutNav = view.findViewById(R.id.layout_navigazione);
        layoutRitorno = view.findViewById(R.id.layout_ritorno_auto);

        mapPreview = view.findViewById(R.id.map_preview_home);

        // --- 2. LOGICA UI ---
        boolean showLogo = aggiornaStatoUI();

        // --- 3. ANIMAZIONI ---
        if (showLogo) {
            TranslateAnimation animLogo = new TranslateAnimation(0, 0, -200, 0);
            animLogo.setDuration(1000);
            animLogo.setFillAfter(true);
            cardLogo.startAnimation(animLogo);

            AlphaAnimation animText = new AlphaAnimation(0.0f, 1.0f);
            animText.setDuration(1500);
            animText.setStartOffset(500);
            titolo.startAnimation(animText);
            sottotitolo.startAnimation(animText);
        }

        TranslateAnimation animBtn = new TranslateAnimation(0, 0, 200, 0);
        animBtn.setDuration(1000);
        if (showLogo) animBtn.setStartOffset(800);
        containerButtons.startAnimation(animBtn);

        // --- 4. LISTENER ---
        view.findViewById(R.id.btn_mappa).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_search));

        view.findViewById(R.id.btn_salva_posizione).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_save));

        view.findViewById(R.id.btn_continua_guida).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav));

        view.findViewById(R.id.btn_ritorna_auto).setOnClickListener(v -> avviaNavigazioneVersoAuto());

        view.findViewById(R.id.btn_elimina_auto).setOnClickListener(v -> {
            SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
            prefs.edit()
                    .remove("auto_salvata")
                    .remove("car_lat")
                    .remove("car_lon")
                    .remove("note_auto")
                    .apply();

            Toast.makeText(requireContext(), "Posizione auto rimossa.", Toast.LENGTH_SHORT).show();
            aggiornaStatoUI(); // Ricarica UI immediata
        });
    }

    private boolean aggiornaStatoUI() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        boolean isNavigazioneAttiva = prefs.getBoolean("navigazione_attiva", false);
        boolean isAutoSalvata = prefs.getBoolean("auto_salvata", false);

        layoutNormal.setVisibility(View.GONE);
        layoutNav.setVisibility(View.GONE);
        layoutRitorno.setVisibility(View.GONE);
        containerCenter.setVisibility(View.VISIBLE);

        if (isNavigazioneAttiva) {
            // Stiamo guidando (o tornando all'auto)
            layoutNav.setVisibility(View.VISIBLE);
            sottotitolo.setText("Navigazione in corso...");
        }
        else if (isAutoSalvata) {
            // Auto parcheggiata
            layoutRitorno.setVisibility(View.VISIBLE);
            sottotitolo.setText("La tua auto è al sicuro.");

            float lat = prefs.getFloat("car_lat", 0);
            float lon = prefs.getFloat("car_lon", 0);
            if (lat != 0 && lon != 0) {
                mostraAnteprimaMappa(lat, lon);
            }
        }
        else {
            // Stato iniziale
            layoutNormal.setVisibility(View.VISIBLE);
            sottotitolo.setText("Trova. Parcheggia. Ritrova.");
        }

        return true;
    }

    private void mostraAnteprimaMappa(double carLat, double carLon) {
        if (mapPreview == null) return;

        mapPreview.setTileSource(TileSourceFactory.MAPNIK);
        mapPreview.setMultiTouchControls(false); // Disabilita tocco per anteprima statica
        mapPreview.setBuiltInZoomControls(false);

        // Blocca interazione tocco (Opzionale ma consigliato per le anteprime)
        mapPreview.setOnTouchListener((v, event) -> true);

        mapPreview.getController().setZoom(17.5);
        GeoPoint carPoint = new GeoPoint(carLat, carLon);
        mapPreview.getController().setCenter(carPoint);

        mapPreview.getOverlays().clear();

        // 1. Marker Auto (Logica Icona Sicura)
        Marker carMarker = new Marker(mapPreview);
        carMarker.setPosition(carPoint);
        carMarker.setTitle("La tua auto");
        carMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Caricamento icona sicuro (Try-Catch come negli altri file)
        try {
            Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_directions_car_24);
            if (icon != null) {
                icon = icon.getConstantState().newDrawable().mutate();
                icon.setTint(Color.RED); // Auto rossa
                carMarker.setIcon(icon);
            }
        } catch (Exception e) {
            // Fallback silenzioso
        }
        mapPreview.getOverlays().add(carMarker);

        // 2. Marker Utente (Solo visualizzazione)
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapPreview);
        myLocationOverlay.enableMyLocation();
        // Rimuovi freccia direzione per pulizia visiva, lascia solo pallino (opzionale)
        mapPreview.getOverlays().add(myLocationOverlay);

        mapPreview.invalidate();
    }

    private void avviaNavigazioneVersoAuto() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        float carLat = prefs.getFloat("car_lat", 0);
        float carLon = prefs.getFloat("car_lon", 0);
        String note = prefs.getString("note_auto", "");

        if (carLat == 0 || carLon == 0) {
            Toast.makeText(requireContext(), "Errore posizione auto", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putFloat("dest_lat", carLat);
        bundle.putFloat("dest_lon", carLon);
        bundle.putString("dest_nome", note.isEmpty() ? "La tua Auto" : "Auto: " + note);
        bundle.putBoolean("dest_is_paid", false);

        prefs.edit()
                .putBoolean("navigazione_attiva", true)
                .putFloat("dest_lat", carLat)
                .putFloat("dest_lon", carLon)
                .putString("dest_nome", "La tua Auto")
                .apply();

        NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav, bundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapPreview != null) mapPreview.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        // Aggiorna stato al ritorno (es. se ho finito navigazione)
        aggiornaStatoUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapPreview != null) mapPreview.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }
}