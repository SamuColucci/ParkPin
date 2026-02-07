package com.example.parkpin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Nascondi la barra in alto
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Trova le viste
        View cardLogo = findViewById(R.id.card_logo);
        View titolo = findViewById(R.id.txt_titolo);
        View sottotitolo = findViewById(R.id.txt_sottotitolo);
        View btnStart = findViewById(R.id.btn_start_app);

        // --- ANIMAZIONI ---
        // 1. Logo che scende dall'alto
        TranslateAnimation animLogo = new TranslateAnimation(0, 0, -200, 0);
        animLogo.setDuration(1000);
        animLogo.setFillAfter(true);
        cardLogo.startAnimation(animLogo);

        // 2. Testo che appare in dissolvenza (Fade In)
        AlphaAnimation animText = new AlphaAnimation(0.0f, 1.0f);
        animText.setDuration(1500);
        animText.setStartOffset(500); // Parte dopo mezzo secondo
        titolo.startAnimation(animText);
        sottotitolo.startAnimation(animText);

        // 3. Bottone che sale dal basso
        TranslateAnimation animBtn = new TranslateAnimation(0, 0, 200, 0);
        animBtn.setDuration(1000);
        animBtn.setStartOffset(800); // Parte quasi alla fine
        btnStart.startAnimation(animBtn);

        // --- LISTENER CLICK ---
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); // Transizione morbida
            finish();
        });
    }
}