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
        public Center center;
    }

    public static class Center {
        public double lat;
        public double lon;
    }

    public static class Tags {
        // Mappiamo il campo JSON "name" alla variabile Java "name"
        @SerializedName("name")
        public String name;

        // Mappiamo "addr:street" (che contiene i due punti) alla variabile "street"
        @SerializedName("addr:street")
        public String street;

        // Mappiamo il costo
        @SerializedName("fee")
        public String fee;

        // Tipo di parcheggio (strada, garage, surface)
        @SerializedName("parking")
        public String parking;

        // Operatore (es. Saba, Comune...) utile se manca il nome
        @SerializedName("operator")
        public String operator;

        @SerializedName("capacity")
        public String capacity;
    }
}