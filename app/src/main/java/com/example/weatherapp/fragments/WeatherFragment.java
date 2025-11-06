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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat; // MỚI
import java.util.ArrayList;
import java.util.Date; // MỚI
import java.util.List;
import java.util.Locale; // MỚI
import java.util.TimeZone; // MỚI
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherFragment extends Fragment implements LocationResultListener {

    // --- Biến thời tiết hiện tại ---
    private final String API_KEY = BuildConfig.API_KEY;
    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private Button refreshButton;
    private EditText cityNameInput;
    private LinearLayout cityInputLayout;
    private ImageView searchIcon;
    private ImageView gpsIcon;

    // --- Biến logic ---
    private LocationHandler locationHandler;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ExecutorService executorService;
    private boolean isGpsMode;

    // --- Biến dự báo hàng GIỜ (Hourly) ---
    private RecyclerView hourlyRecyclerView;
    private HourlyForecastAdapter hourlyAdapter;
    private List<HourlyForecast> hourlyForecasts;

    // --- MỚI: Biến dự báo hàng NGÀY (Daily) ---
    private static final int NUM_DAYS = 5;
    private TextView[] dailyDateViews = new TextView[NUM_DAYS];
    private TextView[] dailyMinTempViews = new TextView[NUM_DAYS];
    private TextView[] dailyMaxTempViews = new TextView[NUM_DAYS];
    private ImageView[] dailyIconViews = new ImageView[NUM_DAYS];
    // ----------------------------------------


    public static WeatherFragment newInstance(boolean isGpsMode) {
        // ... (Giữ nguyên)
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
        executorService = Executors.newSingleThreadExecutor();
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
        return inflater.inflate(R.layout.fragment_weather, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- Gán View thời tiết hiện tại ---
        cityNameText = view.findViewById(R.id.cityNameText);
        temperatureText = view.findViewById(R.id.temperatureText);
        humidityText = view.findViewById(R.id.humidityText);
        windText = view.findViewById(R.id.windText);
        descriptionText = view.findViewById(R.id.descriptionText);
        cityNameInput = view.findViewById(R.id.cityNameInput);
        refreshButton = view.findViewById(R.id.fetchWeatherButton);
        cityInputLayout = view.findViewById(R.id.cityInputLayout);
        searchIcon = view.findViewById(R.id.searchIcon);
        gpsIcon = view.findViewById(R.id.gpsIcon);

        // --- MỚI: Gán View dự báo hàng ngày ---
        initDailyForecastViews(view);
        // ---------------------------------

        locationHandler = new LocationHandler((AppCompatActivity) getActivity(), this);

        // --- Logic chế độ GPS / Tìm kiếm ---
        if (isGpsMode) {
            searchIcon.setVisibility(View.GONE);
            cityInputLayout.setVisibility(View.GONE);
            gpsIcon.setVisibility(View.VISIBLE);
            locationHandler.requestLocationPermission(requestPermissionLauncher);
        } else {
            searchIcon.setVisibility(View.VISIBLE);
            cityInputLayout.setVisibility(View.GONE);
            gpsIcon.setVisibility(View.GONE);
            FetchWeatherData("Hanoi, VN");

            searchIcon.setOnClickListener(v -> {
                if (cityInputLayout.getVisibility() == View.GONE) {
                    cityInputLayout.setVisibility(View.VISIBLE);
                } else {
                    cityInputLayout.setVisibility(View.GONE);
                }
            });
            refreshButton.setOnClickListener(v -> {
                String cityName = cityNameInput.getText().toString().trim();
                if (!cityName.isEmpty()) {
                    // Thêm ", VN" để tìm kiếm chính xác
                    FetchWeatherData(cityName + ", VN");
                } else {
                    cityNameInput.setError("Please enter a city name");
                }
            });
        }

        // --- Cài đặt RecyclerView ---
        hourlyRecyclerView = view.findViewById(R.id.hourlyForecastRecyclerView);
        LinearLayoutManager horizontalLayoutManager =
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        hourlyRecyclerView.setLayoutManager(horizontalLayoutManager);

        // Khởi tạo danh sách rỗng, API sẽ cập nhật
        hourlyForecasts = new ArrayList<>();
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecasts);
        hourlyRecyclerView.setAdapter(hourlyAdapter);
    }

    // =================================================================
    // CÁC HÀM CALLBACK (TỪ LocationHandler)
    // =================================================================

    @Override
    public void onLocationFound(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        FetchWeatherData(lat, lon); // Lấy thời tiết (current, hourly, daily)
        FetchCityNameFromCoords(lat,lon); // Lấy tên thành phố
    }

    @Override
    public void onLocationPermissionDenied() {
        Toast.makeText(getContext(), "Quyền bị từ chối. Đang tải dữ liệu mặc định.", Toast.LENGTH_SHORT).show();
        FetchWeatherData("Hanoi, VN");
    }

    @Override
    public void onLocationError() {
        Toast.makeText(getContext(), "Không thể lấy vị trí. Tải dữ liệu mặc định", Toast.LENGTH_SHORT).show();
        FetchWeatherData("Hanoi, VN");
    }

    // =================================================================
    // CÁC HÀM GỌI API (Đã sửa URL)
    // =================================================================

    private void FetchWeatherData(double lat, double lon) {
        // SỬA LỖI: Bỏ 'daily' khỏi 'exclude'
        String url = "https://api.openweathermap.org/data/3.0/onecall?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric&exclude=minutely,alerts";
        executeWeatherRequest(url, "Lỗi API thời tiết");
    }

    private void FetchWeatherData(String cityName) {
        // ... (Giữ nguyên hàm Geocoding)
        String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q=" + cityName + "&limit=1&appid=" + API_KEY;
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request geoRequest = new Request.Builder().url(geoUrl).build();
            try {
                Response geoResponse = client.newCall(geoRequest).execute();
                if (geoResponse.isSuccessful() && geoResponse.body() != null) {
                    String geoResult = geoResponse.body().string();
                    JSONArray jsonArray = new JSONArray(geoResult);
                    if (jsonArray.length() > 0) {
                        JSONObject geoObject = jsonArray.getJSONObject(0);
                        double lat = geoObject.getDouble("lat");
                        double lon = geoObject.getDouble("lon");
                        FetchWeatherData(lat, lon);
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Không tìm thấy thành phố", Toast.LENGTH_SHORT).show());
                        }
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Lỗi Geocoding", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Lỗi mạng (Geo)", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void executeWeatherRequest(String url, String errorMsg) {
        // ... (Giữ nguyên, không tạo 'ExecutorService' mới)
        executorService.execute(() ->
        {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string();
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

    private void FetchCityNameFromCoords(double lat, double lon) {
        // ... (Giữ nguyên hàm lấy tên thành phố, đã có fallback)
        String reverseGeoUrl = "https://api.openweathermap.org/geo/1.0/reverse?lat=" + lat + "&lon=" + lon + "&limit=1&appid=" + API_KEY;
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(reverseGeoUrl).build();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string();
                    JSONArray jsonArray = new JSONArray(result);
                    if (jsonArray.length() > 0) {
                        JSONObject geoObject = jsonArray.getJSONObject(0);
                        String cityName = geoObject.getString("name");
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                cityNameText.setText(cityName);
                            });
                        }
                    } else { throw new JSONException("Không tìm thấy tên"); }
                } else { throw new IOException("Lỗi API Geocoding"); }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                // Fallback: Hiển thị tọa độ nếu lỗi
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        cityNameText.setText(String.format(Locale.getDefault(), "%.2f, %.2f", lat, lon));
                    });
                }
            }
        });
    }

    // =================================================================
    // HÀM CẬP NHẬT UI (Đã thêm Daily & Hourly)
    // =================================================================

    private void updateUI(String result) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject current = jsonObject.getJSONObject("current");

            // --- Cập nhật thời tiết hiện tại (Current) ---
            double temperature = current.getDouble("temp");
            double humidity = current.getDouble("humidity");
            double windSpeed = current.getDouble("wind_speed");
            String description = current.getJSONArray("weather").getJSONObject(0).getString("description");

            if (!isGpsMode) { // Chỉ cập nhật tên ở chế độ Tìm kiếm
                String searchedName = cityNameInput.getText().toString().trim();
                if (!searchedName.isEmpty()) {
                    cityNameText.setText(searchedName);
                } else {
                    cityNameText.setText("Hanoi");
                }
            } // (Chế độ GPS sẽ được cập nhật bởi FetchCityNameFromCoords)

            temperatureText.setText(String.format(Locale.getDefault(), "%.0f°C", temperature));
            humidityText.setText(String.format(Locale.getDefault(), "%.0f%%", humidity));
            windText.setText(String.format(Locale.getDefault(), "%.0f Km/h", windSpeed));
            descriptionText.setText(description);

            // --- Cập nhật dự báo hàng GIỜ (Hourly) ---
            if (jsonObject.has("hourly")) {
                JSONArray hourlyArray = jsonObject.getJSONArray("hourly");
                updateHourlyForecast(hourlyArray);
            }

            // --- Cập nhật dự báo hàng NGÀY (Daily) ---
            if (jsonObject.has("daily")) {
                JSONArray dailyArray = jsonObject.getJSONArray("daily");
                updateDailyForecast(dailyArray);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi phân tích dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }

    // =================================================================
    // HÀM HELPER (MỚI)
    // =================================================================

    /**
     * HÀM MỚI: Gán các View (ô dự báo) hàng ngày
     */
    private void initDailyForecastViews(View view) {
        // Giả sử 5 ô của bạn có ID là: day1_date, day1_min_temp, day1_max_temp, day1_weather_icon
        // Và tương tự cho day2, day3, day4, day5
        try {
            dailyDateViews[0] = view.findViewById(R.id.day1_date);
            dailyMinTempViews[0] = view.findViewById(R.id.day1_min_temp);
            dailyMaxTempViews[0] = view.findViewById(R.id.day1_max_temp);
            dailyIconViews[0] = view.findViewById(R.id.day1_weather_icon);

            dailyDateViews[1] = view.findViewById(R.id.day2_date);
            dailyMinTempViews[1] = view.findViewById(R.id.day2_min_temp);
            dailyMaxTempViews[1] = view.findViewById(R.id.day2_max_temp);
            dailyIconViews[1] = view.findViewById(R.id.day2_weather_icon);

            dailyDateViews[2] = view.findViewById(R.id.day3_date);
            dailyMinTempViews[2] = view.findViewById(R.id.day3_min_temp);
            dailyMaxTempViews[2] = view.findViewById(R.id.day3_max_temp);
            dailyIconViews[2] = view.findViewById(R.id.day3_weather_icon);

            dailyDateViews[3] = view.findViewById(R.id.day4_date);
            dailyMinTempViews[3] = view.findViewById(R.id.day4_min_temp);
            dailyMaxTempViews[3] = view.findViewById(R.id.day4_max_temp);
            dailyIconViews[3] = view.findViewById(R.id.day4_weather_icon);

            dailyDateViews[4] = view.findViewById(R.id.day5_date);
            dailyMinTempViews[4] = view.findViewById(R.id.day5_min_temp);
            dailyMaxTempViews[4] = view.findViewById(R.id.day5_max_temp);
            dailyIconViews[4] = view.findViewById(R.id.day5_weather_icon);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi: Thiếu ID của View dự báo hàng ngày trong XML", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * HÀM MỚI: Phân tích JSON "hourly" và cập nhật RecyclerView
     */
    private void updateHourlyForecast(JSONArray hourlyArray) throws JSONException {
        hourlyForecasts.clear(); // Xóa data mẫu/cũ

        // API trả về 48 giờ, ta chỉ lấy 24 giờ
        int hoursToDisplay = Math.min(hourlyArray.length(), 24);

        for (int i = 0; i < hoursToDisplay; i++) {
            JSONObject hourForecast = hourlyArray.getJSONObject(i);

            long dt = hourForecast.getLong("dt");
            String time = getHourOfDay(dt);
            String temp = String.format(Locale.getDefault(), "%.0f°", hourForecast.getDouble("temp"));
            String wind = String.format(Locale.getDefault(), "%.0f Km/h", hourForecast.getDouble("wind_speed"));
            String iconCode = hourForecast.getJSONArray("weather").getJSONObject(0).getString("icon");
            int iconResId = getWeatherIconResource(iconCode);

            hourlyForecasts.add(new HourlyForecast(time, temp, wind, iconResId));
        }

        // Báo cho Adapter biết data đã thay đổi
        hourlyAdapter.notifyDataSetChanged();
    }

    /**
     * HÀM MỚI: Phân tích JSON "daily" và cập nhật 5 ô dự báo
     */
    private void updateDailyForecast(JSONArray dailyArray) throws JSONException {
        // API trả về 8 ngày, ta chỉ lấy 5 ngày
        int daysToDisplay = Math.min(dailyArray.length(), NUM_DAYS);

        for (int i = 0; i < daysToDisplay; i++) {
            JSONObject dayForecast = dailyArray.getJSONObject(i);

            // 1. Cập nhật ngày
            long dt = dayForecast.getLong("dt");
            if (i == 0) {
                dailyDateViews[i].setText("Today");
            } else if (i == 1) {
                dailyDateViews[i].setText("Tomorrow");
            } else {
                dailyDateViews[i].setText(getDayOfWeek(dt));
            }

            // 2. Cập nhật nhiệt độ Min/Max
            JSONObject tempObject = dayForecast.getJSONObject("temp");
            double minTemp = tempObject.getDouble("min");
            double maxTemp = tempObject.getDouble("max");

            dailyMinTempViews[i].setText(String.format(Locale.getDefault(), "%.0f°", minTemp));
            dailyMaxTempViews[i].setText(String.format(Locale.getDefault(), "%.0f°", maxTemp));

            // 3. Cập nhật Icon
            String iconCode = dayForecast.getJSONArray("weather").getJSONObject(0).getString("icon");
            dailyIconViews[i].setImageResource(getWeatherIconResource(iconCode));
        }
    }

    /**
     * HÀM HELPER: Đổi timestamp (dt) sang Giờ (ví dụ: "3 PM", "Now")
     */
    private String getHourOfDay(long dt) {
        try {
            Date date = new Date(dt * 1000L); // nhân 1000 vì dt là giây
            SimpleDateFormat sdf = new SimpleDateFormat("h a", Locale.getDefault()); // ví dụ: "3 PM"
            sdf.setTimeZone(TimeZone.getDefault()); // Dùng múi giờ của máy

            // So sánh với giờ hiện tại
            long now = System.currentTimeMillis();
            if (Math.abs(now - date.getTime()) < 3600000) { // Gần 1 tiếng
                return "Now";
            }
            return sdf.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HÀM HELPER: Đổi timestamp (dt) sang Tên Thứ (ví dụ: "Mon")
     */
    private String getDayOfWeek(long dt) {
        try {
            Date date = new Date(dt * 1000L);
            SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault()); // ví dụ: "Mon"
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HÀM HELPER: Đổi iconCode (ví dụ: "01d") sang ID của ảnh (ví dụ: R.drawable.ic_01d)
     */
    private int getWeatherIconResource(String iconCode) {
        switch (iconCode) {
            case "01d": return R.drawable.ic_01d;
            case "01n": return R.drawable.ic_01n;
            case "02d": return R.drawable.ic_02d;
            case "02n": return R.drawable.ic_02n;
            case "03d": case "03n": return R.drawable.ic_03d;
            case "04d": case "04n": return R.drawable.ic_04d;
            case "09d": case "09n": return R.drawable.ic_09d;
            case "10d": return R.drawable.ic_10d;
            case "10n": return R.drawable.ic_10n;
            default: return R.drawable.ic_01d;
        }
    }
}