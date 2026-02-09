package com.example.parkpin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Riferimenti alle View
        View cardLogo = findViewById(R.id.card_logo);
        View titolo = findViewById(R.id.txt_titolo);
        View sottotitolo = findViewById(R.id.txt_sottotitolo);

        View containerButtons = findViewById(R.id.container_buttons);
        LinearLayout layoutNormal = findViewById(R.id.layout_normal);
        LinearLayout layoutNav = findViewById(R.id.layout_navigazione);

        // Nuovi Bottoni
        View btnSalva = findViewById(R.id.btn_salva_posizione);
        View btnMappa = findViewById(R.id.btn_mappa);
        View btnContinua = findViewById(R.id.btn_continua_guida);

        // --- CONTROLLO LOGICO ---
        SharedPreferences prefs = getSharedPreferences("ParkPinNav", Context.MODE_PRIVATE);
        boolean isNavigating = prefs.getBoolean("navigazione_attiva", false);

        if (isNavigating) {
            // Se guidi: Mostra solo "Continua"
            layoutNormal.setVisibility(View.GONE);
            layoutNav.setVisibility(View.VISIBLE);
        } else {
            // Se sei fermo: Mostra "Salva" e "Mappa"
            layoutNormal.setVisibility(View.VISIBLE);
            layoutNav.setVisibility(View.GONE);
        }

        // --- ANIMAZIONI ---
        TranslateAnimation animLogo = new TranslateAnimation(0, 0, -200, 0);
        animLogo.setDuration(1000);
        animLogo.setFillAfter(true);
        cardLogo.startAnimation(animLogo);

        AlphaAnimation animText = new AlphaAnimation(0.0f, 1.0f);
        animText.setDuration(1500);
        animText.setStartOffset(500);
        titolo.startAnimation(animText);
        sottotitolo.startAnimation(animText);

        TranslateAnimation animBtn = new TranslateAnimation(0, 0, 200, 0);
        animBtn.setDuration(1000);
        animBtn.setStartOffset(800);
        containerButtons.startAnimation(animBtn);

        // --- GESTIONE CLICK ---

        // 1. TASTO SALVA POSIZIONE
        btnSalva.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            // Passiamo un "segnale" alla Mappa per dirle di salvare subito la posizione
            intent.putExtra("AZIONE_SALVA_AUTO", true);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        // 2. TASTO MAPPA (Normale)
        btnMappa.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        // 3. TASTO CONTINUA (Navigazione)
        btnContinua.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent); // La mappa riprenderà da sola la linea blu
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }
}