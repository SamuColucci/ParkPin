package com.example.parkpin;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
  - Gestisce la UI a tutto schermo (Edge-to-Edge).
  - Richiedere i permessi fondamentali al primo avvio (GPS, Notifiche).
  - Monitorare costantemente il GPS: se disattivato o senza permessi,
  blocca la navigazione mostrando un Dialog obbligatorio.
 */

public class MainActivity extends AppCompatActivity {

    private AlertDialog gpsDialog;
    private BroadcastReceiver gpsReceiver;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                checkGpsStatus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        gpsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
                    checkGpsStatus();
                }
            }
        };

        gestisciPermessiIniziali();
    }

    private void gestisciPermessiIniziali() {
        List<String> permessiDaChiedere = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permessiDaChiedere.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permessiDaChiedere.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permessiDaChiedere.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permessiDaChiedere.isEmpty()) {
            requestPermissionsLauncher.launch(permessiDaChiedere.toArray(new String[0]));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gpsReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        checkGpsStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gpsReceiver != null) unregisterReceiver(gpsReceiver);
    }

    private void checkGpsStatus() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        try {
            isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}

        if (!hasPermission || !isGpsEnabled) {
            showGpsDialog(!hasPermission);
        } else {
            if (gpsDialog != null && gpsDialog.isShowing()) {
                gpsDialog.dismiss();
            }
        }
    }

    private void showGpsDialog(boolean isPermissionIssue) {
        if (gpsDialog != null && gpsDialog.isShowing()) return;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_gps_required, null);

        TextView title = view.findViewById(R.id.txt_dialog_title);
        TextView desc = view.findViewById(R.id.txt_dialog_msg);
        com.google.android.material.button.MaterialButton btn = view.findViewById(R.id.btn_activate_gps);

        if (isPermissionIssue) {
            if(title != null) title.setText("Permessi Necessari");
            if(desc != null) desc.setText("ParkPin richiede il permesso di posizione per funzionare. Senza di esso non potrai usare le mappe.");
            btn.setText("DAI PERMESSO");
        } else {
            if(title != null) title.setText("GPS Disattivato");
            if(desc != null) desc.setText("ParkPin richiede il GPS attivo per funzionare correttamente. Riattivalo ora.");
            btn.setText("ATTIVA GPS");
        }

        gpsDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        if (gpsDialog.getWindow() != null) {
            gpsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btn.setOnClickListener(v -> {
            if (isPermissionIssue) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } else {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        gpsDialog.show();
    }
}