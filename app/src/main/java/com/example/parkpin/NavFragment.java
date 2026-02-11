package com.example.parkpin;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Calendar;

public class NavFragment extends Fragment {

    private MapView map = null;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline currentRoute = null;
    private Marker destMarker = null;
    private TextView txtDestinazione, txtDistanza, txtTempo, txtIstruzione;

    private GeoPoint destinazionePoint;
    private String destinazioneNome;
    private boolean isPagamento;
    private GeoPoint ultimaPosizioneCalcolo = null;
    private boolean isPrimaDisegnoEffettuato = false;
    private boolean navigationCompleted = false;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private boolean isRitornoAllAuto = false;

    private com.google.android.material.progressindicator.LinearProgressIndicator progressLoader;

    private boolean notificaInviataArrivo = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) avviaNavigazioneGPS();
                else {
                    Toast.makeText(requireContext(), "Serve GPS!", Toast.LENGTH_SHORT).show();
                    tornaAllaHome();
                }
            });

    public NavFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        return inflater.inflate(R.layout.fragment_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navigationCompleted = false;
        isPrimaDisegnoEffettuato = false;

        // UI Setup
        txtDestinazione = view.findViewById(R.id.txt_nav_destinazione);
        // Assicurati di aver aggiunto questa TextView nell'XML del NavFragment
        TextView txtNavNota = view.findViewById(R.id.txt_nav_nota);

        txtDistanza = view.findViewById(R.id.txt_nav_distanza);
        txtTempo = view.findViewById(R.id.txt_nav_tempo);
        txtIstruzione = view.findViewById(R.id.txt_nav_istruzione);
        map = view.findViewById(R.id.map);
        progressLoader = view.findViewById(R.id.progress_loader_nav);

        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

        float lat, lon;
        String notaRecuperata = "";

        if (getArguments() != null && getArguments().containsKey("dest_lat")) {
            // CASO 1: Navigazione appena avviata (riceviamo i dati dal Bundle)
            lat = getArguments().getFloat("dest_lat", 0);
            lon = getArguments().getFloat("dest_lon", 0);
            destinazioneNome = getArguments().getString("dest_nome", "Destinazione");
            notaRecuperata = getArguments().getString("dest_nota", ""); // Recupero nota dal Bundle
            isPagamento = getArguments().getBoolean("dest_is_paid", false);

            // Salviamo nelle preferenze inclusa la nota
            prefs.edit().putBoolean("navigazione_attiva", true)
                    .putFloat("dest_lat", lat)
                    .putFloat("dest_lon", lon)
                    .putString("dest_nome", destinazioneNome)
                    .putString("dest_nota", notaRecuperata) // Salvataggio nota
                    .putBoolean("dest_is_paid", isPagamento)
                    .apply();

        } else if (prefs.getBoolean("navigazione_attiva", false)) {
            // CASO 2: Ripresa dopo chiusura app (recuperiamo dalle SharedPreferences)
            lat = prefs.getFloat("dest_lat", 0);
            lon = prefs.getFloat("dest_lon", 0);
            destinazioneNome = prefs.getString("dest_nome", "Destinazione");
            notaRecuperata = prefs.getString("dest_nota", ""); // Recupero nota dalle Prefs
            isPagamento = prefs.getBoolean("dest_is_paid", false);

        } else {
            // CASO 3: Errore o nessun dato trovato
            tornaAllaHome();
            return;
        }

        // Determiniamo se è un ritorno all'auto
        destinazionePoint = new GeoPoint(lat, lon);
        isRitornoAllAuto = "La tua Auto".equals(destinazioneNome);

        // Gestione della Nota sulla UI
        if (txtNavNota != null) {
            if (isRitornoAllAuto && !notaRecuperata.isEmpty()) {
                txtNavNota.setVisibility(View.VISIBLE);
                txtNavNota.setText("Nota: " + notaRecuperata);
            } else {
                txtNavNota.setVisibility(View.GONE);
            }
        }

        // Configurazione UI e Mappa
        txtDestinazione.setText(destinazioneNome);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(18.5);
        map.getController().setCenter(destinazionePoint);

        mettiMarkerDestinazione(destinazionePoint, destinazioneNome);

        // Listeners
        view.findViewById(R.id.btn_stop_navigazione).setOnClickListener(v -> stopNavigazioneManuale());
        view.findViewById(R.id.btn_nav_google_maps).setOnClickListener(v -> apriGoogleMaps());
        view.findViewById(R.id.fab_centra_posizione).setOnClickListener(v -> {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                map.getController().animateTo(myLocationOverlay.getMyLocation());
                myLocationOverlay.enableFollowLocation();
            }
        });

        // Avvio GPS
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            avviaNavigazioneGPS();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void avviaNavigazioneGPS() {
        if (navigationCompleted) return;
        notificaInviataArrivo = false; // Reset ogni volta che parte la nav

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();

        int iconResId = isRitornoAllAuto ?
                R.drawable.baseline_directions_walk_24 :
                R.drawable.baseline_directions_car_24;

        Bitmap iconaUtente = getBitmapFromVectorDrawable(requireContext(), iconResId);
        if (iconaUtente != null) {
            myLocationOverlay.setPersonIcon(iconaUtente);
            myLocationOverlay.setDirectionIcon(iconaUtente);
        }

        map.getOverlays().add(myLocationOverlay);

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (navigationCompleted) return;
                GeoPoint posAttuale = new GeoPoint(location.getLatitude(), location.getLongitude());

                if (!isPrimaDisegnoEffettuato) {
                    disegnaPercorsoSullaMappa(posAttuale, destinazionePoint);
                    isPrimaDisegnoEffettuato = true;
                    map.getController().animateTo(posAttuale);
                    myLocationOverlay.enableFollowLocation();
                }

                double distanzaMetri = posAttuale.distanceToAsDouble(destinazionePoint);

                // SOGLIA ARRIVO: 40 metri
                if (distanzaMetri < 40) {
                    // INVIA NOTIFICA DI SISTEMA SE È UN RITORNO ALL'AUTO
                    if (isRitornoAllAuto && !notificaInviataArrivo) {
                        inviaNotificaPushArrivo();
                        notificaInviataArrivo = true;
                    }

                    txtIstruzione.setText(isRitornoAllAuto ? "Sei arrivato all'auto! 🚗" : "Sei arrivato al parcheggio! 🎉");
                    txtDistanza.setText("0 m");
                    gestisciArrivo();
                    return;
                }

                if (ultimaPosizioneCalcolo == null || posAttuale.distanceToAsDouble(ultimaPosizioneCalcolo) > 50) {
                    disegnaPercorsoSullaMappa(posAttuale, destinazionePoint);
                    ultimaPosizioneCalcolo = posAttuale;
                }
            }
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    // METODO SUPPORTO PER LA NOTIFICA PUSH
    private void inviaNotificaPushArrivo() {
        Context context = getContext();
        if (context == null) return;

        String channelId = "arrival_channel";
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Creazione Canale (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Notifiche Arrivo", android.app.NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.baseline_directions_car_24)
                .setContentTitle("ParkPin")
                .setContentText("Sei arrivato alla tua auto! 🚗")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(2002, builder.build());
        }
    }

    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        if (navigationCompleted) return;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (progressLoader != null) {
                    // Se pedone -> Barra Verde, altrimenti Blu
                    int colore = isRitornoAllAuto ? Color.parseColor("#4CAF50") : Color.BLUE;
                    progressLoader.setIndicatorColor(colore);
                    progressLoader.setVisibility(View.VISIBLE);
                }
            });
        }

        new Thread(() -> {
            try {
                OSRMRoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");

                // Impostiamo il profilo (Pedone o Auto)
                if (isRitornoAllAuto) {
                    roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT);
                } else {
                    roadManager.setMean(OSRMRoadManager.MEAN_BY_CAR);
                }

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                // Richiesta al server OSRM
                Road road = roadManager.getRoad(waypoints);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // 2. NASCONDIAMO IL LOADER: il calcolo è terminato
                        if (progressLoader != null) progressLoader.setVisibility(View.GONE);

                        if (road.mStatus == Road.STATUS_OK && !navigationCompleted) {
                            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);

                            // CAMBIO COLORE DINAMICO: Verde per pedone (#4CAF50), Blu per auto
                            int colorePercorso = isRitornoAllAuto ? Color.parseColor("#4CAF50") : Color.BLUE;

                            roadOverlay.getOutlinePaint().setColor(colorePercorso);
                            roadOverlay.getOutlinePaint().setStrokeWidth(15.0f);

                            // Rimuoviamo il vecchio percorso prima di aggiungere il nuovo
                            if (currentRoute != null) map.getOverlays().remove(currentRoute);
                            currentRoute = roadOverlay;
                            map.getOverlays().add(currentRoute);
                            map.invalidate();

                            // Aggiornamento testi distanza
                            double distKm = road.mLength;
                            if (distKm < 1.0) {
                                txtDistanza.setText(String.format("%d m", (int)(distKm * 1000)));
                            } else {
                                txtDistanza.setText(String.format("%.1f km", distKm));
                            }

                            // Aggiornamento tempo stimato
                            double durataSec = road.mDuration;
                            int minuti = (int) (durataSec / 60);
                            txtTempo.setText(minuti < 1 ? "< 1 min" : minuti + " min");

                            // Aggiorna l'istruzione di base
                            if (isRitornoAllAuto) {
                                txtIstruzione.setText("Segui il percorso a piedi");
                            } else {
                                txtIstruzione.setText("Segui il percorso stradale");
                            }
                        } else if (road.mStatus != Road.STATUS_OK) {
                            // Gestione errore di rete o server
                            Toast.makeText(getContext(), "Errore nel calcolo del percorso", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Nascondiamo il loader anche in caso di crash/eccezione
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (progressLoader != null) progressLoader.setVisibility(View.GONE);
                    });
                }
            }
        }).start();
    }

    private void mettiMarkerDestinazione(GeoPoint punto, String titolo) {
        if (destMarker != null) map.getOverlays().remove(destMarker);
        destMarker = new Marker(map);
        destMarker.setPosition(punto);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        if (isRitornoAllAuto) {
            // DESTINAZIONE: LA TUA AUTO ROSSA
            destMarker.setTitle("La tua Auto");
            Drawable iconAuto = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_directions_car_24);
            if (iconAuto != null) {
                destMarker.setIcon(iconAuto);
            }
        } else {
            // DESTINAZIONE: PARCHEGGIO GENERICO
            destMarker.setTitle("Parcheggio");
            destMarker.setIcon(ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default));
        }

        map.getOverlays().add(destMarker);
        map.invalidate();
    }

    private void gestisciArrivo() {
        if (navigationCompleted) return;
        navigationCompleted = true;
        pulisciRisorse();

        if (getActivity() == null || getContext() == null) return;

        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

        if (isRitornoAllAuto) {
            // ==========================================
            // CASO A: BENTORNATO ALL'AUTO
            // ==========================================

            // 1. ANNULLA LA NOTIFICA NEL SISTEMA
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(requireContext(), ParkingAlarmReceiver.class);
                // Il PendingIntent deve essere identico a quello creato in NotificationHelper per essere riconosciuto
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                        requireContext(),
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                );

                if (am != null) {
                    am.cancel(pi); // Cancella l'allarme programmato
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Puliamo i dati della sosta dalle SharedPreferences
            prefs.edit()
                    .remove("navigazione_attiva")
                    .remove("auto_salvata")
                    .remove("car_lat")
                    .remove("car_lon")
                    .remove("note_auto")
                    .remove("orario_scadenza_timer")
                    .apply();

            // 3. Mostriamo il Dialog di conferma senza chiudere subito
            new AlertDialog.Builder(requireContext())
                    .setTitle("Obiettivo raggiunto! 🚗")
                    .setMessage("Sei arrivato alla tua auto. Il timer del parcheggio è stato annullato correttamente.")
                    .setPositiveButton("OTTIMO", (d, w) -> {
                        tornaAllaHome();
                    })
                    .setCancelable(false)
                    .show();

        } else {
            // ==========================================
            // CASO B: ARRIVO A UN NUOVO PARCHEGGIO
            // ==========================================

            float latArrivo = prefs.getFloat("dest_lat", 0);
            float lonArrivo = prefs.getFloat("dest_lon", 0);
            String nomeArrivo = prefs.getString("dest_nome", "Parcheggio");

            // Salviamo la posizione per il futuro ritorno
            prefs.edit()
                    .putBoolean("auto_salvata", true)
                    .putFloat("car_lat", latArrivo)
                    .putFloat("car_lon", lonArrivo)
                    .putString("note_auto", "Parcheggiato presso: " + nomeArrivo)
                    .apply();

            prefs.edit().remove("navigazione_attiva").apply();

            new AlertDialog.Builder(requireContext())
                    .setTitle("Sei arrivato! 📍")
                    .setMessage("La posizione dell'auto è stata salvata correttamente.\n\nVuoi impostare un promemoria per la scadenza del ticket?")
                    .setPositiveButton("SÌ, IMPOSTA 🔔", (d, w) -> mostraSelettoreOrario())
                    .setNegativeButton("NO, FINE", (d, w) -> tornaAllaHome())
                    .setCancelable(false)
                    .show();
        }
    }

    private void mostraSelettoreOrario() {
        Calendar oraAttuale = Calendar.getInstance();
        int h = oraAttuale.get(Calendar.HOUR_OF_DAY);
        int m = oraAttuale.get(Calendar.MINUTE);

        TimePickerDialog timePicker = new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> {
                    // Utilizza la Utility creata precedentemente
                    NotificationHelper.prenotaAvviso(requireContext(), hourOfDay, minute);
                    tornaAllaHome();
                }, h, m, true);

        timePicker.setOnCancelListener(dialog -> tornaAllaHome());
        timePicker.show();
    }

    private void stopNavigazioneManuale() {
        if (navigationCompleted) return;
        navigationCompleted = true;
        pulisciRisorse();

        requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                .remove("navigazione_attiva")
                .remove("dest_lat").remove("dest_lon").remove("dest_nome")
                .remove("dest_is_paid")
                .apply();

        Toast.makeText(requireContext(), "Navigazione terminata.", Toast.LENGTH_SHORT).show();
        tornaAllaHome();
    }

    private void apriGoogleMaps() {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destinazionePoint.getLatitude() + "," + destinazionePoint.getLongitude());
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        try { startActivity(mapIntent); } catch (Exception e) {}
    }

    private void pulisciRisorse() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    private void tornaAllaHome() {
        try {
            NavHostFragment.findNavController(this).navigate(R.id.action_nav_to_home);
        } catch (Exception e) {
            NavHostFragment.findNavController(this).popBackStack(R.id.homeFragment, false);
        }
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override public void onPause() { super.onPause(); if(map!=null) map.onPause(); pulisciRisorse(); }
    @Override public void onResume() {
        super.onResume();
        if(map!=null) map.onResume();
        if(!navigationCompleted && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) avviaNavigazioneGPS();
    }

    private void inviaNotificaArrivo() {
        Context context = requireContext();
        String channelId = "parking_arrival_channel";
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Creazione del canale per Android 8.0+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Arrivo a destinazione",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.baseline_directions_car_24) // Usa la tua icona auto
                .setContentTitle("Sei arrivato!")
                .setContentText("Sei giunto in prossimità della tua auto.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(1001, builder.build());
        }
    }
}