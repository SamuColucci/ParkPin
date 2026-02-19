package com.example.parkpin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.splashscreen.SplashScreen;

public class MainActivity extends AppCompatActivity {

    // Launcher per gestire la risposta dell'utente alla richiesta permessi
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    // L'utente ha negato il permesso: avvisiamolo delle conseguenze
                    Toast.makeText(this, "Attenzione: senza notifiche non riceverai gli avvisi di scadenza parcheggio!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);

        // Attiva la modalità a tutto schermo (barra trasparente)
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        // Nasconde la barra viola in alto
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Gestisce i margini di sistema (Status Bar, Navigation Bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- AVVIA RICHIESTA PERMESSI ---
        chiediPermessoNotifiche();
    }

    private void chiediPermessoNotifiche() {
        // Il permesso POST_NOTIFICATIONS è obbligatorio da Android 13 (API 33) in su
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                // Mostra il popup di sistema per le notifiche
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}