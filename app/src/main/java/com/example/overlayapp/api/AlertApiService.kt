package com.example.overlayapp.api

import com.example.overlayapp.data.AlertEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AlertApiService {
    @GET("alerts")
    suspend fun getAlerts(): List<AlertEntity>

    @POST("alerts")
    suspend fun sendAlert(@Body alert: AlertEntity): AlertEntity
}
