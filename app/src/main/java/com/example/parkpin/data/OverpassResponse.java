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

    // Classe interna per i dettagli (nome, tipo)
    public static class Tags {
        @SerializedName("name")
        public String nome; // Nome del parcheggio (es. "Parcheggio Stazione")

        @SerializedName("access")
        public String accesso; // es. "private", "customers", "public"
    }
}