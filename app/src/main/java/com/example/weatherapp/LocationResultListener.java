package com.example.weatherapp;

import android.location.Location;
public interface LocationResultListener {
    void onLocationFound(Location location);
    void onLocationPermissionDenied();
    void onLocationError();
}
