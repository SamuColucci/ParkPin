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
    private TextView sottotitolo, txtTimerScadenza;

    private MapView mapPreviewSaved;
    private MapView mapPreviewCurrent;
    private MapView mapPreviewNav;

    private MyLocationNewOverlay myLocationOverlay;    // Per mappa stato normale
    private MyLocationNewOverlay myLocationNavOverlay; // Per mappa stato navigazione

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
        sottotitolo = view.findViewById(R.id.txt_sottotitolo);
        txtTimerScadenza = view.findViewById(R.id.txt_home_timer_scadenza);
        containerCenter = view.findViewById(R.id.container_center);
        containerButtons = view.findViewById(R.id.container_buttons);
        layoutNormal = view.findViewById(R.id.layout_normal);
        layoutNav = view.findViewById(R.id.layout_navigazione);
        layoutRitorno = view.findViewById(R.id.layout_ritorno_auto);

        mapPreviewSaved = view.findViewById(R.id.map_preview_home);
        mapPreviewCurrent = view.findViewById(R.id.map_preview_current);
        mapPreviewNav = view.findViewById(R.id.map_preview_nav);
        // Modifica Nota Posizione
        view.findViewById(R.id.btn_edit_note).setOnClickListener(v -> mostraDialogModificaNota());

        aggiornaStatoUI();

        // --- LISTENERS ---

        // Ricerca Parcheggio
        view.findViewById(R.id.btn_mappa).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_search));

        // Salva Posizione Manuale
        view.findViewById(R.id.btn_salva_posizione).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_save));

        // Continua Navigazione Esistente
        view.findViewById(R.id.btn_continua_guida).setOnClickListener(v -> {
            SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
            float lat = prefs.getFloat("dest_lat", 0);
            float lon = prefs.getFloat("dest_lon", 0);
            String nome = prefs.getString("dest_nome", "Parcheggio");
            String nota = prefs.getString("dest_nota", "");

            if (lat != 0) {
                Bundle b = new Bundle();
                b.putFloat("dest_lat", lat);
                b.putFloat("dest_lon", lon);
                b.putString("dest_nome", nome);
                b.putString("dest_nota", nota);
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav, b);
            }
        });

        // Avvia Ritorno all'Auto
        view.findViewById(R.id.btn_ritorna_auto).setOnClickListener(v -> avviaNavigazioneVersoAuto());

        // CONDIVIDI POSIZIONE (Nuovo!)
        view.findViewById(R.id.btn_share_location).setOnClickListener(v -> condividiPosizioneAuto());

        // Elimina Auto Salvata
        view.findViewById(R.id.btn_elimina_auto).setOnClickListener(v -> {
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                android.content.Intent intent = new android.content.Intent(requireContext(), ParkingAlarmReceiver.class);
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                        requireContext(), 0, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                if (am != null) am.cancel(pi);
            } catch (Exception e) { e.printStackTrace(); }

            requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                    .remove("auto_salvata").remove("car_lat").remove("car_lon")
                    .remove("note_auto").remove("orario_scadenza_timer").apply();

            aggiornaStatoUI();
            Toast.makeText(requireContext(), "Posizione e timer eliminati", Toast.LENGTH_SHORT).show();
        });

        // Interrompi Navigazione Attiva
        view.findViewById(R.id.btn_stop_nav_home).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Interrompere?")
                    .setMessage("Vuoi davvero annullare la navigazione verso il parcheggio?")
                    .setPositiveButton("Sì, annulla", (dialog, which) -> {
                        requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                                .putBoolean("navigazione_attiva", false)
                                .remove("dest_lat").remove("dest_lon").remove("dest_nome").remove("dest_is_paid")
                                .apply();
                        aggiornaStatoUI();
                        Toast.makeText(requireContext(), "Navigazione annullata", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    // METODO SUPPORTO PER LA CONDIVISIONE
    private void condividiPosizioneAuto() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        float lat = prefs.getFloat("car_lat", 0);
        float lon = prefs.getFloat("car_lon", 0);
        String notaRaw = prefs.getString("note_auto", "");

        if (lat != 0 && lon != 0) {
            // Pulizia della stringa: rimuoviamo i prefissi ridondanti
            String notaPulita = notaRaw.replace("Parcheggiato Presso :", "")
                    .replace("Nota:", "")
                    .trim();

            String uri = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;

            // Costruiamo il messaggio finale
            StringBuilder messaggio = new StringBuilder();
            messaggio.append("🚗 La mia auto è parcheggiata qui:\n").append(uri);

            if (!notaPulita.isEmpty()) {
                messaggio.append("\n\n📍 Info: ").append(notaPulita);
            }

            android.content.Intent sendIntent = new android.content.Intent();
            sendIntent.setAction(android.content.Intent.ACTION_SEND);
            sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, messaggio.toString());
            sendIntent.setType("text/plain");

            android.content.Intent shareIntent = android.content.Intent.createChooser(sendIntent, "Condividi posizione tramite:");
            startActivity(shareIntent);
        } else {
            Toast.makeText(requireContext(), "Errore: Posizione non trovata", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostraDialogModificaNota() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        String notaAttuale = prefs.getString("note_auto", "");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Modifica Nota");

        // Creiamo un EditText per l'input
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(notaAttuale);
        input.setSelection(input.getText().length()); // Cursore alla fine

        // Aggiungiamo un po' di margine all'EditText
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50; params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Salva", (dialog, which) -> {
            String nuovaNota = input.getText().toString().trim();
            prefs.edit().putString("note_auto", nuovaNota).apply();
            Toast.makeText(requireContext(), "Nota aggiornata ✅", Toast.LENGTH_SHORT).show();
            // Se avessi una TextView che mostra la nota in Home, chiameresti aggiornaStatoUI() qui
        });

        builder.setNegativeButton("Annulla", null);
        builder.show();
    }

    private void aggiornaStatoUI() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        boolean isNavigazioneAttiva = prefs.getBoolean("navigazione_attiva", false);
        boolean isAutoSalvata = prefs.getBoolean("auto_salvata", false);
        String orarioScadenza = prefs.getString("orario_scadenza_timer", null);

        layoutNormal.setVisibility(View.GONE);
        layoutNav.setVisibility(View.GONE);
        layoutRitorno.setVisibility(View.GONE);

        if (txtTimerScadenza != null) {
            if (orarioScadenza != null) {
                txtTimerScadenza.setVisibility(View.VISIBLE);
                txtTimerScadenza.setText("⏳ Scadenza: " + orarioScadenza);
            } else {
                txtTimerScadenza.setVisibility(View.GONE);
            }
        }

        if (isNavigazioneAttiva) {
            layoutNav.setVisibility(View.VISIBLE);
            sottotitolo.setText("Navigazione in corso...");
            float lat = prefs.getFloat("dest_lat", 0);
            float lon = prefs.getFloat("dest_lon", 0);
            if (lat != 0) mostraMappaDestinazione(lat, lon);

        } else if (isAutoSalvata) {
            layoutRitorno.setVisibility(View.VISIBLE);
            sottotitolo.setText("La tua auto è al sicuro.");
            float lat = prefs.getFloat("car_lat", 0);
            float lon = prefs.getFloat("car_lon", 0);
            String nota = prefs.getString("note_auto", "");
            if (lat != 0) mostraMappaSaved(lat, lon);

        } else {
            layoutNormal.setVisibility(View.VISIBLE);
            sottotitolo.setText("Trova. Parcheggia. Ritrova.");
            mostraMappaPosizioneAttuale();
        }
    }

    private void mostraMappaDestinazione(double lat, double lon) {
        if (mapPreviewNav == null) return;
        mapPreviewNav.setTileSource(TileSourceFactory.MAPNIK);
        mapPreviewNav.setMultiTouchControls(false);
        mapPreviewNav.getController().setZoom(17.5);
        mapPreviewNav.getOverlays().clear();

        // Marker della destinazione
        GeoPoint p = new GeoPoint(lat, lon);
        Marker m = new Marker(mapPreviewNav);
        m.setPosition(p);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle("Destinazione");
        Drawable icon = ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default);
        if(icon != null) m.setIcon(icon);
        mapPreviewNav.getOverlays().add(m);

        // Aggiunta overlay posizione attuale su mappa navigazione
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (myLocationNavOverlay != null) {
                myLocationNavOverlay.disableMyLocation();
            }
            myLocationNavOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapPreviewNav);
            myLocationNavOverlay.enableMyLocation();
            mapPreviewNav.getOverlays().add(myLocationNavOverlay);

            myLocationNavOverlay.runOnFirstFix(() -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (myLocationNavOverlay != null && myLocationNavOverlay.getMyLocation() != null) {
                            mapPreviewNav.getController().animateTo(myLocationNavOverlay.getMyLocation());
                        }
                    });
                }
            });
        } else {
            mapPreviewNav.getController().setCenter(p);
        }
        mapPreviewNav.invalidate();
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
            if(icon!=null){ m.setIcon(icon); }
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

        if (myLocationOverlay != null) {
            mapPreviewCurrent.getOverlays().remove(myLocationOverlay);
            myLocationOverlay.disableMyLocation();
            myLocationOverlay = null;
        }

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapPreviewCurrent);
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
        mapPreviewCurrent.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mapPreviewSaved != null) mapPreviewSaved.onResume();
        if(mapPreviewCurrent != null) mapPreviewCurrent.onResume();
        if(mapPreviewNav != null) mapPreviewNav.onResume();
        aggiornaStatoUI();
    }

    private void avviaNavigazioneVersoAuto() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        float lat = prefs.getFloat("car_lat", 0);
        float lon = prefs.getFloat("car_lon", 0);
        String nota = prefs.getString("note_auto", ""); // Recupera la nota salvata

        if (lat == 0) return;

        Bundle b = new Bundle();
        b.putFloat("dest_lat", lat);
        b.putFloat("dest_lon", lon);
        b.putString("dest_nome", "La tua Auto");
        b.putString("dest_nota", nota); // Aggiungi la nota al bundle

        prefs.edit()
                .putBoolean("navigazione_attiva", true)
                .putFloat("dest_lat", lat)
                .putFloat("dest_lon", lon)
                .putString("dest_nota", nota) // Salva anche nelle preferenze per riaperture future
                .apply();

        NavHostFragment.findNavController(this).navigate(R.id.action_home_to_nav, b);
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mapPreviewSaved != null) mapPreviewSaved.onPause();
        if(mapPreviewCurrent != null) mapPreviewCurrent.onPause();
        if(mapPreviewNav != null) mapPreviewNav.onPause();
        if(myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        if(myLocationNavOverlay != null) myLocationNavOverlay.disableMyLocation();
    }
}