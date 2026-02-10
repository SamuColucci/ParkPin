package com.example.parkpin;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Attiva la modalità a tutto schermo (barra trasparente)
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        // --- AGGIUNTA FONDAMENTALE: Nasconde la barra viola in alto ---
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // -------------------------------------------------------------

        // Gestisce i margini per non finire sotto la fotocamera o la barra di sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}