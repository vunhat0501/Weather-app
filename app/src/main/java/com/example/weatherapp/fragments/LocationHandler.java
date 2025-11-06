package com.example.weatherapp.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

public class LocationHandler {
    private AppCompatActivity activity;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationResultListener locationResultListener;

    public LocationHandler(AppCompatActivity activity, LocationResultListener listener) {
        this.activity = activity;
        this.locationResultListener = listener;
        this.fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    public void requestLocationPermission(ActivityResultLauncher<String> requestPermissionLauncher) {
        if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLastKnownLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    public void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "lỗi kiểm tra quyền GPS", Toast.LENGTH_SHORT).show();
            return;
        }

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(activity, location -> {
            if (location != null) {
                locationResultListener.onLocationFound(location);
            } else {
                locationResultListener.onLocationError();
            }
        });
    }
}
