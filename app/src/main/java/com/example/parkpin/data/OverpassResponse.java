package com.example.parkpin.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OverpassResponse {
    @SerializedName("elements")
    public List<Elemento> elementi;

    // Classe interna per il singolo parcheggio
    public static class Elemento {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lon")
        public double lon;

        @SerializedName("tags")
        public Tags tags;
    }

    // Classe interna per i dettagli
    public static class Tags {
        @SerializedName("name")
        public String nome;

        @SerializedName("access")
        public String accesso;

        @SerializedName("fee")
        public String fee;      // "yes" = a pagamento, "no" = gratis

        @SerializedName("capacity")
        public String capacity; // Numero posti (es. "50")

        @SerializedName("operator")
        public String operator; // Chi lo gestisce

        @SerializedName("parking")
        public String tipo;     // "surface", "underground"

        // --- NUOVI CAMPI AGGIUNTI PER LA RICERCA STRADA ---
        @SerializedName("addr:street")
        public String strada;   // Es. "Via Roma"

        @SerializedName("addr:housenumber")
        public String numeroCivico; // Es. "12"
    }
}