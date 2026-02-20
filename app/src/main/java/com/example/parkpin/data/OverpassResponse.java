package com.example.parkpin.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
//Classe per il parsing delle risposte API
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

    //Classe che permette di mappare il campo JSON con una variabile Java
    public static class Tags {
        @SerializedName("name")
        public String name;

        @SerializedName("addr:street")
        public String street;

        @SerializedName("fee")
        public String fee;

        @SerializedName("parking")
        public String parking;

        @SerializedName("operator")
        public String operator;

    }
}