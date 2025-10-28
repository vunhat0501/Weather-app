package com.example.weatherapp;


import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.location.Location;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements LocationResultListener {

    private final String API_KEY = BuildConfig.API_KEY;
    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private ImageView wertherIcon;
    private Button refreshButton;
    private EditText cityNameInput;
    private LocationHandler locationHandler;
    private ActivityResultLauncher<String> requestPermissionLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        cityNameText = findViewById(R.id.cityNameText);
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        windText = findViewById(R.id.windText);
        descriptionText = findViewById(R.id.descriptionText);
        wertherIcon = findViewById(R.id.weatherIcon);
        refreshButton = findViewById(R.id.fetchWeatherButton);
        cityNameInput = findViewById(R.id.cityNameInput);
        locationHandler = new LocationHandler(this, this);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted){
                        locationHandler.getLastKnownLocation();
                    } else {
                        onLocationPermissionDenied();
                    }
                });

        refreshButton.setOnClickListener(v -> {
            String cityName = cityNameInput.getText().toString().trim();
            if (!cityName.isEmpty()) {
                FetchWeatherData(cityName);
            } else {
                cityNameInput.setError("Plesas enter a city name");
            }
        });

        locationHandler.requestLocationPermission(requestPermissionLauncher);
         ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
             Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
             v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
             return  insets;
         });
    }
    @Override
    public void onLocationFound(Location location){
        FetchWeatherData(location.getLatitude(), location.getLongitude());
    }
    @Override
    public void  onLocationPermissionDenied() {
        Toast.makeText(this, "Quyền bị từ chối. Đang tải dữ liệu mặc định.", Toast.LENGTH_SHORT).show();
        FetchWeatherData("HaNoi");
    }
    @Override
    public void onLocationError(){
        Toast.makeText(MainActivity.this, "Không thể lấy vị trí. Tải dữ liệu mặc định",Toast.LENGTH_SHORT).show();
        FetchWeatherData("HaNoi");
    }
    private void FetchWeatherData(double lat, double lon){
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric";
        executeWeatherRequest(url, "Lỗi API");
    }
    private void FetchWeatherData(String cityName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&appid=" + API_KEY + "&units=metric";
        executeWeatherRequest(url, "Không tìm thấy thành phố");
    }
    private void executeWeatherRequest(String url, String errorMsg){
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() ->
        {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null){
                    String result = response.body().string();
                    runOnUiThread(() -> updateUI(result));
                } else {
                    runOnUiThread(()-> Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e){
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private  void updateUI(String result){
        if(result != null){
            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONObject main = jsonObject.getJSONObject("main");
                double temperature = main.getDouble("temp");
                double humidity = main.getDouble("humidity");
                double windSpeed = jsonObject.getJSONObject("wind").getDouble("speed");
                String description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");
                String iconCode = jsonObject.getJSONArray("weather").getJSONObject(0).getString("icon");
                String resourceName = "ic_" + iconCode;
                int resId = getResources().getIdentifier(resourceName, "drawable", getPackageName());
                wertherIcon.setImageResource(resId);
                cityNameText.setText(jsonObject.getString("name"));
                temperatureText.setText(String.format("%.0f°C", temperature));
                humidityText.setText(String.format("%.0f%%", humidity));
                windText.setText(String.format("%.0f Km/h", windSpeed));
                descriptionText.setText(description);
            } catch (JSONException e){
                e.printStackTrace();
            }
        }
    }
}