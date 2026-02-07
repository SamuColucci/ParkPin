package com.example.parkpin;

import com.example.parkpin.data.OverpassResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OverpassService {
    // Overpass usa un linguaggio strano, noi gli passiamo la query come stringa "data"
    @GET("interpreter")
    Call<OverpassResponse> cercaParcheggi(@Query("data") String query);
}