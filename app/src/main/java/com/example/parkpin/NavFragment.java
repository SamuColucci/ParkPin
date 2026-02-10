package com.example.parkpin;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;

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

        txtDestinazione = view.findViewById(R.id.txt_nav_destinazione);
        txtDistanza = view.findViewById(R.id.txt_nav_distanza);
        txtTempo = view.findViewById(R.id.txt_nav_tempo);
        txtIstruzione = view.findViewById(R.id.txt_nav_istruzione);
        map = view.findViewById(R.id.map);

        SharedPreferences prefs = requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);

        if (getArguments() != null) {
            float lat = getArguments().getFloat("dest_lat", 0);
            float lon = getArguments().getFloat("dest_lon", 0);
            destinazioneNome = getArguments().getString("dest_nome", "Destinazione");
            isPagamento = getArguments().getBoolean("dest_is_paid", false);
            destinazionePoint = new GeoPoint(lat, lon);

            prefs.edit().putBoolean("navigazione_attiva", true)
                    .putFloat("dest_lat", lat)
                    .putFloat("dest_lon", lon)
                    .putString("dest_nome", destinazioneNome)
                    .putBoolean("dest_is_paid", isPagamento)
                    .apply();
        } else if (prefs.getBoolean("navigazione_attiva", false)) {
            float lat = prefs.getFloat("dest_lat", 0);
            float lon = prefs.getFloat("dest_lon", 0);
            destinazioneNome = prefs.getString("dest_nome", "Destinazione");
            isPagamento = prefs.getBoolean("dest_is_paid", false);
            destinazionePoint = new GeoPoint(lat, lon);
        } else {
            tornaAllaHome();
            return;
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
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), map);
        myLocationOverlay.enableMyLocation();

        Bitmap iconaMacchina = getBitmapFromVectorDrawable(requireContext(), R.drawable.baseline_directions_car_24);
        if (iconaMacchina != null) {
            myLocationOverlay.setPersonIcon(iconaMacchina);
            myLocationOverlay.setDirectionIcon(iconaMacchina);
        }
        map.getOverlays().add(myLocationOverlay);

        locationManager = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        locationListener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(@NonNull android.location.Location location) {
                if (navigationCompleted) return;
                GeoPoint posAttuale = new GeoPoint(location.getLatitude(), location.getLongitude());

                if (!isPrimaDisegnoEffettuato) {
                    disegnaPercorsoSullaMappa(posAttuale, destinazionePoint);
                    isPrimaDisegnoEffettuato = true;
                    map.getController().animateTo(posAttuale);
                    myLocationOverlay.enableFollowLocation();
                }

                double distanzaMetri = posAttuale.distanceToAsDouble(destinazionePoint);
                if (distanzaMetri < 40) { // ARRIVATO
                    txtIstruzione.setText("Sei arrivato! 🎉");
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
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        try {
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    private void disegnaPercorsoSullaMappa(GeoPoint start, GeoPoint end) {
        if (navigationCompleted) return;
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(requireContext(), "ParkPin-UserAgent");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);
                Road road = roadManager.getRoad(waypoints);

                if (road.mStatus == Road.STATUS_OK && !navigationCompleted) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(Color.BLUE);
                    roadOverlay.getOutlinePaint().setStrokeWidth(15.0f);
                    double distKm = road.mLength;
                    double durataSec = road.mDuration;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (navigationCompleted) return;
                            if (currentRoute != null) map.getOverlays().remove(currentRoute);
                            currentRoute = roadOverlay;
                            map.getOverlays().add(currentRoute);
                            map.invalidate();
                            if (distKm < 1.0) txtDistanza.setText(String.format("%d m", (int)(distKm * 1000)));
                            else txtDistanza.setText(String.format("%.1f km", distKm));
                            int minuti = (int) (durataSec / 60);
                            txtTempo.setText(minuti < 1 ? "< 1 min" : minuti + " min");
                        });
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void mettiMarkerDestinazione(GeoPoint punto, String titolo) {
        if (destMarker != null) map.getOverlays().remove(destMarker);
        destMarker = new Marker(map);
        destMarker.setPosition(punto);
        destMarker.setTitle("Arrivo");
        destMarker.setIcon(ContextCompat.getDrawable(requireContext(), org.osmdroid.library.R.drawable.marker_default));
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(destMarker);
        map.invalidate();
    }

    // --- CANCELLAZIONE TOTALE ALL'ARRIVO ---
    private void gestisciArrivo() {
        if (navigationCompleted) return;
        navigationCompleted = true;
        pulisciRisorse();

        Toast.makeText(requireContext(), "Arrivato a destinazione!", Toast.LENGTH_LONG).show();

        // 1. CANCELLA TUTTO ORA.
        // Se riapri l'app dopo questo punto, sei nella Home pulita.
        requireActivity().getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE)
                .edit()
                .remove("navigazione_attiva")
                .remove("dest_lat")
                .remove("dest_lon")
                .remove("dest_nome")
                .remove("dest_is_paid")
                .apply();

        // 2. PASSA I DATI SOLO IN MEMORIA (Bundle)
        // Questi dati vivono solo per il passaggio tra NavFragment -> SaveCarFragment
        Bundle bundle = new Bundle();
        bundle.putBoolean("is_paid", isPagamento);
        bundle.putFloat("lat_arrivo", (float) destinazionePoint.getLatitude());
        bundle.putFloat("lon_arrivo", (float) destinazionePoint.getLongitude());

        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == R.id.navFragment) {
            navController.navigate(R.id.action_nav_to_save, bundle);
        }
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
        android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=" + destinazionePoint.getLatitude() + "," + destinazionePoint.getLongitude());
        android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
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
        try { NavHostFragment.findNavController(this).popBackStack(R.id.homeFragment, false); } catch (Exception e) {}
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
}