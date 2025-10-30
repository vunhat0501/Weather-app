package com.example.weatherapp.fragments;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherapp.BuildConfig;
import com.example.weatherapp.R;
import com.example.weatherapp.adapter.HourlyForecastAdapter;
import com.example.weatherapp.hourlyforecast.HourlyForecast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherFragment extends Fragment implements LocationResultListener {

    // --- All your variables from MainActivity go here ---
    private final String API_KEY = BuildConfig.API_KEY;
    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private Button refreshButton;
    private EditText cityNameInput;
    private LocationHandler locationHandler;
    private ActivityResultLauncher<String> requestPermissionLauncher;

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
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        locationHandler.getLastKnownLocation();
                    } else {
                        onLocationPermissionDenied();
                    }
                });
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
        cityNameText = view.findViewById(R.id.cityNameText);
        temperatureText = view.findViewById(R.id.temperatureText);
        humidityText = view.findViewById(R.id.humidityText);
        windText = view.findViewById(R.id.windText);
        descriptionText = view.findViewById(R.id.descriptionText);
        cityNameInput = view.findViewById(R.id.cityNameInput);
        refreshButton = view.findViewById(R.id.fetchWeatherButton);
        // Find the city input block
        cityInputLayout = view.findViewById(R.id.cityInputLayout);
        searchIcon = view.findViewById(R.id.searchIcon);
        locationHandler = new LocationHandler((AppCompatActivity) getActivity(), this);
        // This is the magic:
        if (isGpsMode) {
            searchIcon.setVisibility(View.GONE);
            cityInputLayout.setVisibility(View.GONE);
            // TODO: Put all your GPS logic here
            // 1. Request location permissions
            // 2. Get user's coordinates
            // 3. Call weather API with those coordinates
            locationHandler.requestLocationPermission(requestPermissionLauncher);
        } else {
            searchIcon.setVisibility(View.VISIBLE);
            cityInputLayout.setVisibility(View.GONE); // Explicitly hide on create
            FetchWeatherData("Hanoi");
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
            refreshButton.setOnClickListener(v -> {
                String cityName = cityNameInput.getText().toString().trim();
                if (!cityName.isEmpty()) {
                    FetchWeatherData(cityName);
                } else {
                    cityNameInput.setError("Please enter a city name");
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
    @Override
    public void onLocationFound(Location location) {
        FetchWeatherData(location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onLocationPermissionDenied() {
        Toast.makeText(getContext(), "Quyền bị từ chối. Đang tải dữ liệu mặc định.", Toast.LENGTH_SHORT).show();
        FetchWeatherData("Hanoi");
    }

    @Override
    public void onLocationError() {
        Toast.makeText(getContext(), "Không thể lấy vị trí. Tải dữ liệu mặc định", Toast.LENGTH_SHORT).show();
        FetchWeatherData("Hanoi");
    }
    private void FetchWeatherData(double lat, double lon) {
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric";
        executeWeatherRequest(url, "Lỗi API");
    }

    private void FetchWeatherData(String cityName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&appid=" + API_KEY + "&units=metric";
        executeWeatherRequest(url, "Không tìm thấy thành phố");
    }

    private void executeWeatherRequest(String url, String errorMsg) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() ->
        {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string();
                    // MỚI: Phải kiểm tra getActivity() != null trong Fragment
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateUI(result));
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Lỗi mạng", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
    private void updateUI(String result) {
        // TODO: Phân tích JSON để cập nhật cả RecyclerView (hourlyForecasts)
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject main = jsonObject.getJSONObject("main");
            double temperature = main.getDouble("temp");
            double humidity = main.getDouble("humidity");
            double windSpeed = jsonObject.getJSONObject("wind").getDouble("speed");
            String description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");
            String iconCode = jsonObject.getJSONArray("weather").getJSONObject(0).getString("icon");

            // Cập nhật các View chính
            cityNameText.setText(jsonObject.getString("name"));
            temperatureText.setText(String.format("%.0f°C", temperature));
            humidityText.setText(String.format("%.0f%%", humidity));
            windText.setText(String.format("%.0f Km/h", windSpeed));
            descriptionText.setText(description);



        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi phân tích dữ liệu", Toast.LENGTH_SHORT).show();
        }
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