package com.example.parkpin;

import com.example.parkpin.data.OverpassResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OverpassService {
    // Endpoint per ottenere la lista dei parcheggi,
    // specifica che la richiesta è di tipo get
    @GET("interpreter")
    Call<OverpassResponse> cercaParcheggi(@Query("data") String query);
}