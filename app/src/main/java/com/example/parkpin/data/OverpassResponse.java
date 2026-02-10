package com.example.parkpin.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OverpassResponse {

    @SerializedName("elements")
    public List<Elemento> elementi;

    public static class Elemento {
        public long id;
        public double lat;
        public double lon;
        public Tags tags;

        // --- QUESTA È LA PARTE CHE MANCAVA ---
        public Center center;
        // -------------------------------------
    }

    // --- NUOVA CLASSE PER IL CENTRO DELLE AREE ---
    public static class Center {
        public double lat;
        public double lon;
    }
    // ---------------------------------------------

    public static class Tags {
        @SerializedName("name")
        public String nome;

        @SerializedName("addr:street")
        public String strada;

        @SerializedName("addr:housenumber")
        public String numeroCivico;

        public String operator;
        public String fee;      // "yes", "no", o importo
        public String capacity; // Numero posti

        // Altri tag utili se vuoi espandere in futuro
        public String access;
        public String parking;
    }
}