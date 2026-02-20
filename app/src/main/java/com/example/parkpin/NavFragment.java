package com.example.parkpin;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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

    private LinearLayout loadingContainer;
    private LinearLayout layoutError;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) avviaNavigazioneGPS();
                else {
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

        txtDestinazione = view.findViewById(R.id.txt_nav_destinazione);
        TextView txtNavNota = view.findViewById(R.id.txt_nav_nota);
        txtDistanza = view.findViewById(R.id.txt_nav_distanza);
        txtTempo = view.findViewById(R.id.txt_nav_tempo);
        txtIstruzione = view.findViewById(R.id.txt_nav_istruzione);
        map = view.findViewById(R.id.map);
        progressLoader = view.findViewById(R.id.progress_loader_nav);
        loadingContainer = view.findViewById(R.id.loading_container);
        layoutError = view.findViewById(R.id.layout_error_retry);

        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

        float lat, lon;
        String notaRecuperata = "";

        if (getArguments() != null && getArguments().containsKey("dest_lat")) {
            lat = getArguments().getFloat("dest_lat", 0);
            lon = getArguments().getFloat("dest_lon", 0);
            destinazioneNome = getArguments().getString("dest_nome", "Destinazione");
            notaRecuperata = getArguments().getString("dest_nota", "");
            isPagamento = getArguments().getBoolean("dest_is_paid", false);

            prefs.edit().putBoolean("navigazione_attiva", true)
                    .putFloat("dest_lat", lat)
                    .putFloat("dest_lon", lon)
                    .putString("dest_nome", destinazioneNome)
                    .putString("dest_nota", notaRecuperata)
                    .putBoolean("dest_is_paid", isPagamento)
                    .apply();
        } else if (prefs.getBoolean("navigazione_attiva", false)) {
            lat = prefs.getFloat("dest_lat", 0);
            lon = prefs.getFloat("dest_lon", 0);
            destinazioneNome = prefs.getString("dest_nome", "Destinazione");
            notaRecuperata = prefs.getString("dest_nota", "");
            isPagamento = prefs.getBoolean("dest_is_paid", false);
        } else {
            tornaAllaHome();
            return;
        }

        destinazionePoint = new GeoPoint(lat, lon);
        isRitornoAllAuto = "La tua Auto".equals(destinazioneNome);
        if (isRitornoAllAuto) {
            NotificationHelper.schedulaProssimoControlloPosizione(requireContext());
        }

        if (txtNavNota != null) {
            if (isRitornoAllAuto && !notaRecuperata.isEmpty()) {
                txtNavNota.setVisibility(View.VISIBLE);
                txtNavNota.setText("Nota: " + notaRecuperata);
            } else {
                txtNavNota.setVisibility(View.GONE);
            }
        }

        txtDestinazione.setText(destinazioneNome);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(18.5);
        map.getController().setCenter(destinazionePoint);
        mettiMarkerDestinazione(destinazionePoint, destinazioneNome);

        view.findViewById(R.id.btn_stop_navigazione).setOnClickListener(v -> stopNavigazioneManuale());
        view.findViewById(R.id.btn_nav_google_maps).setOnClickListener(v -> apriGoogleMaps());
        view.findViewById(R.id.fab_centra_posizione).setOnClickListener(v -> {
            if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                map.getController().animateTo(myLocationOverlay.getMyLocation());
                myLocationOverlay.enableFollowLocation();
            }
        });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            avviaNavigazioneGPS();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void avviaNavigazioneGPS() {
        if (navigationCompleted) return;
        notificaInviataArrivo = false;

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

                if (distanzaMetri < 40) {
                    if (isRitornoAllAuto && !notificaInviataArrivo) {
                        NotificationHelper.inviaNotificaArrivoImmediata(requireContext());
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
            if (getActivity() != null) {
                getActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
        } catch (SecurityException e) { e.printStackTrace(); }
    }


    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        if (navigationCompleted) return;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (loadingContainer != null) {
                    loadingContainer.setVisibility(View.VISIBLE);
                    loadingContainer.bringToFront();
                }
                if (layoutError != null) layoutError.setVisibility(View.GONE);
            });
        }

        new Thread(() -> {
            try {
                //OSRMRoadManager per il recupero dei dati stradali
                OSRMRoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");
                roadManager.setMean(isRitornoAllAuto ? OSRMRoadManager.MEAN_BY_FOOT : OSRMRoadManager.MEAN_BY_CAR);

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                Road road = roadManager.getRoad(waypoints);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);

                        if (road.mStatus == Road.STATUS_OK && !navigationCompleted) {
                            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                            int colore = isRitornoAllAuto ? Color.parseColor("#4CAF50") : Color.BLUE;
                            roadOverlay.getOutlinePaint().setColor(colore);
                            roadOverlay.getOutlinePaint().setStrokeWidth(15.0f);

                            if (currentRoute != null) map.getOverlays().remove(currentRoute);
                            currentRoute = roadOverlay;
                            map.getOverlays().add(currentRoute);
                            map.invalidate();

                            double distKm = road.mLength;
                            txtDistanza.setText(distKm < 1.0 ? (int)(distKm * 1000) + " m" : String.format("%.1f km", distKm));
                            txtTempo.setText((int)(road.mDuration / 60) + " min");
                        } else {
                            if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (loadingContainer != null) loadingContainer.setVisibility(View.GONE);
                        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
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
            destMarker.setTitle("La tua Auto");
            Drawable iconAuto = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_directions_car_24);
            if (iconAuto != null) {
                destMarker.setIcon(iconAuto);
            }
        } else {
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
            mostraDialogArrivoAuto();
        } else {
            mostraDialogArrivoParcheggio(prefs);
        }
    }
    private void mostraDialogArrivoParcheggio(SharedPreferences prefs) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_parking_reached, null);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        float latArrivo = prefs.getFloat("dest_lat", 0);
        float lonArrivo = prefs.getFloat("dest_lon", 0);
        boolean isPaid = prefs.getBoolean("dest_is_paid", false);

        dialogView.findViewById(R.id.btn_save_here).setOnClickListener(v -> {
            dialog.dismiss();

            prefs.edit().remove("navigazione_attiva").apply();

            Bundle bundle = new Bundle();
            bundle.putFloat("lat_arrivo", latArrivo);
            bundle.putFloat("lon_arrivo", lonArrivo);
            bundle.putBoolean("is_paid", isPaid);

            NavHostFragment.findNavController(this).navigate(R.id.action_nav_to_save, bundle);
        });

        dialogView.findViewById(R.id.btn_skip_save).setOnClickListener(v -> {
            dialog.dismiss();

            prefs.edit().remove("navigazione_attiva")
                    .remove("dest_lat").remove("dest_lon")
                    .remove("dest_nome").remove("dest_is_paid")
                    .apply();

            Toast.makeText(requireContext(), "Navigazione completata", Toast.LENGTH_SHORT).show();
            tornaAllaHome();
        });

        dialog.show();
    }

    private void mostraDialogArrivoAuto() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_target_reached, null);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_finish_arrival).setOnClickListener(v -> {

            cancellaAllarmePosizione(requireContext());
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(requireContext(), ParkingAlarmReceiver.class);
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                        requireContext(), 0, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                );
                if (am != null) am.cancel(pi);
            } catch (Exception e) {}

            requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                    .remove("navigazione_attiva")
                    .remove("auto_salvata")
                    .remove("car_lat")
                    .remove("car_lon")
                    .remove("note_auto")
                    .remove("dest_lat")
                    .remove("dest_lon")
                    .remove("dest_nome")
                    .remove("dest_nota")
                    .remove("orario_scadenza_timer")
                    .apply();

            Toast.makeText(requireContext(), "Bentornato alla tua auto! 🚗", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            tornaAllaHome();
        });

        dialog.show();
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

    @Override
    public void onResume() {
        super.onResume();
        if(map!=null) map.onResume();
        cancellaAllarmePosizione(requireContext());

        if(!navigationCompleted && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            avviaNavigazioneGPS();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(map != null) map.onPause();
        if (!navigationCompleted && isRitornoAllAuto) {
            NotificationHelper.schedulaProssimoControlloPosizione(requireContext());
        }
    }

    private void stopNavigazioneManuale() {
        if (navigationCompleted) return;
        navigationCompleted = true;
        cancellaAllarmePosizione(requireContext());
        pulisciRisorse();

        requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE).edit()
                .remove("navigazione_attiva")
                .remove("dest_lat").remove("dest_lon").remove("dest_nome")
                .remove("dest_is_paid")
                .apply();

        Toast.makeText(requireContext(), "Navigazione terminata.", Toast.LENGTH_SHORT).show();
        tornaAllaHome();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (navigationCompleted) {
            pulisciRisorse();
        }
    }
    private void cancellaAllarmePosizione(Context context) {
        android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, LocationCheckReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 100, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }
}