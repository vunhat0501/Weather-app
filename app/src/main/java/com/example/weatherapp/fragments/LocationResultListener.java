package com.example.weatherapp.fragments;

import android.location.Location;
public interface LocationResultListener {
    void onLocationFound(Location location);
    void onLocationPermissionDenied();
    void onLocationError();
}
