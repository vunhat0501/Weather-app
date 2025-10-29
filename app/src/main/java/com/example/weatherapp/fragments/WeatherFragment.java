package com.example.weatherapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherapp.BuildConfig;
import com.example.weatherapp.R;
import com.example.weatherapp.adapter.HourlyForecastAdapter;
import com.example.weatherapp.hourlyforecast.HourlyForecast;

import java.util.ArrayList;
import java.util.List;

public class WeatherFragment extends Fragment {

    // --- All your variables from MainActivity go here ---
    private final String API_KEY = BuildConfig.API_KEY;

    private RecyclerView hourlyRecyclerView;
    private HourlyForecastAdapter hourlyAdapter;
    private List<HourlyForecast> hourlyForecasts;

    private boolean isGpsMode;
    private LinearLayout cityInputLayout;
    private ImageView searchIcon;

    // A special "constructor" for Fragments
    public static WeatherFragment newInstance(boolean isGpsMode) {
        WeatherFragment fragment = new WeatherFragment();
        Bundle args = new Bundle();
        args.putBoolean("IS_GPS_MODE", isGpsMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isGpsMode = getArguments().getBoolean("IS_GPS_MODE");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_weather, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- All your onCreate logic from MainActivity goes here ---

        // Find the city input block
        cityInputLayout = view.findViewById(R.id.cityInputLayout);
        searchIcon = view.findViewById(R.id.searchIcon);

        // This is the magic:
        if (isGpsMode) {
            cityInputLayout.setVisibility(View.GONE);

            // TODO: Put all your GPS logic here
            // 1. Request location permissions
            // 2. Get user's coordinates
            // 3. Call weather API with those coordinates

        } else {
            searchIcon.setVisibility(View.VISIBLE);
            cityInputLayout.setVisibility(View.GONE); // Explicitly hide on create

            // TODO: Put your manual "Change City" button logic here
            // Set the click listener for the icon
            searchIcon.setOnClickListener(v -> {
                // When clicked, toggle the visibility of the input block
                if (cityInputLayout.getVisibility() == View.GONE) {
                    cityInputLayout.setVisibility(View.VISIBLE);
                } else {
                    cityInputLayout.setVisibility(View.GONE);
                }
            });
        }

        // ... (find your other views: cityNameText, etc.)

        // 1. Find the RecyclerView
        hourlyRecyclerView = view.findViewById(R.id.hourlyForecastRecyclerView);

        // 2. Create the Layout Manager
        LinearLayoutManager horizontalLayoutManager =
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        hourlyRecyclerView.setLayoutManager(horizontalLayoutManager);

        // 3. Create sample data
        createSampleData();

        // 4. Create and set the adapter
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecasts);
        hourlyRecyclerView.setAdapter(hourlyAdapter);
    }

    // Your helper method is now part of the fragment
    private void createSampleData() {
        hourlyForecasts = new ArrayList<>();
        hourlyForecasts.add(new HourlyForecast("Now", "25°", "5 km/h", R.drawable.ic_01d));
        hourlyForecasts.add(new HourlyForecast("3 PM", "24°", "6 km/h", R.drawable.ic_01d));
        hourlyForecasts.add(new HourlyForecast("4 PM", "22°", "7 km/h", R.drawable.ic_01d));
        hourlyForecasts.add(new HourlyForecast("5 PM", "21°", "7 km/h", R.drawable.ic_01d));
        hourlyForecasts.add(new HourlyForecast("6 PM", "20°", "5 km/h", R.drawable.ic_01d));
        hourlyForecasts.add(new HourlyForecast("7 PM", "19°", "4 km/h", R.drawable.ic_01d));
        // ... add all 24 hours here
    }
}