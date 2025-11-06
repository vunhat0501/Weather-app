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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private ImageView gpsIcon;
    private ExecutorService executorService;

    // --- THÊM MỚI: Các TextView cho chi tiết thời tiết ---
    private TextView uvText, sunSetText, pressureText, feelsLikeText, windDirection;

    // --- Mảng để giữ các View của dự báo hàng ngày ---
    private final TextView[] dailyDateViews = new TextView[5];
    private final TextView[] dailyMinTempViews = new TextView[5];
    private final TextView[] dailyMaxTempViews = new TextView[5];
    private final ImageView[] dailyIconViews = new ImageView[5];


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
        gpsIcon = view.findViewById(R.id.gpsIcon);
        locationHandler = new LocationHandler((AppCompatActivity) getActivity(), this);

        // --- Gọi hàm để tìm các view dự báo hàng ngày ---
        initDailyForecastViews(view);

        // --- THÊM MỚI: Tìm các View chi tiết mới ---
        uvText = view.findViewById(R.id.uvText);
        sunSetText = view.findViewById(R.id.sunSetText);
        pressureText = view.findViewById(R.id.pressureText);
        feelsLikeText = view.findViewById(R.id.feelsLikeText);
        windDirection = view.findViewById(R.id.windDirection);
        // --- KẾT THÚC THÊM MỚI ---

        // This is the magic:
        if (isGpsMode) {
            searchIcon.setVisibility(View.GONE);
            cityInputLayout.setVisibility(View.GONE);
            searchIcon.setVisibility(View.GONE);
            gpsIcon.setVisibility(View.VISIBLE);
            locationHandler.requestLocationPermission(requestPermissionLauncher);
        } else {
            searchIcon.setVisibility(View.VISIBLE);
            cityInputLayout.setVisibility(View.GONE); // Explicitly hide on create
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
                    FetchWeatherData(cityName);
                } else {
                    cityNameInput.setError("Please enter a city name");
                }
            });
        }

        // 1. Find the RecyclerView
        hourlyRecyclerView = view.findViewById(R.id.hourlyForecastRecyclerView);
        // 2. Create the Layout Manager
        LinearLayoutManager horizontalLayoutManager =
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        hourlyRecyclerView.setLayoutManager(horizontalLayoutManager);

        // --- SỬA ĐỔI: Khởi tạo danh sách rỗng để tránh NullPointerException ---
        hourlyForecasts = new ArrayList<>();

        // 4. Create and set the adapter
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecasts);
        hourlyRecyclerView.setAdapter(hourlyAdapter);
    }
    @Override
    public void onLocationFound(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        FetchWeatherData(lat, lon);
        FetchCityNameFromCoords(lat,lon);
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
    private void FetchWeatherData(double lat, double lon) {
        // Lấy cả hourly và daily
        String url = "https://api.openweathermap.org/data/3.0/onecall?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric&exclude=minutely,alerts";
        executeWeatherRequest(url, "Lỗi API thời tiết");
    }

    private void FetchWeatherData(String cityName) {
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
    // hàm chuyển kinh độ vĩ độ về tên thành phố
    private void FetchCityNameFromCoords(double lat, double lon) {
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
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }
    private void updateUI(String result) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject current = jsonObject.getJSONObject("current");

            // --- Cập nhật thời tiết hiện tại (Phần trên cùng) ---
            double temperature = current.getDouble("temp");
            double humidity = current.getDouble("humidity");
            double windSpeed = current.getDouble("wind_speed"); // Đây là m/s
            String description = current.getJSONArray("weather").getJSONObject(0).getString("description");

            if (isGpsMode) {
                // Tên thành phố sẽ được cập nhật bởi FetchCityNameFromCoords
            } else {
                String searchedName = cityNameInput.getText().toString().trim();
                if (!searchedName.isEmpty()) {
                    cityNameText.setText(searchedName);
                } else {
                    cityNameText.setText("Hanoi");
                }
            }

            temperatureText.setText(String.format("%.0f°C", temperature));
            descriptionText.setText(description);

            // --- Cập nhật các ô Chi tiết thời tiết ---
            // Độ ẩm (Humidity) - Đã có sẵn trong layout, gán lại
            humidityText.setText(String.format("%.0f%%", humidity));

            // Tốc độ gió (Wind Speed) - Đã có sẵn trong layout, gán lại
            // --- SỬA ĐỔI: Chuyển m/s sang km/h (nhân 3.6) ---
            windText.setText(String.format(Locale.getDefault(), "%.1f km/h", windSpeed * 3.6));

            // --- THÊM MỚI: Lấy và cập nhật dữ liệu chi tiết ---
            double uvi = current.getDouble("uvi");
            long sunsetTime = current.getLong("sunset");
            double pressure = current.getDouble("pressure");
            double feelsLike = current.getDouble("feels_like");
            double windDeg = current.getDouble("wind_deg"); // Độ gió

            // Gán dữ liệu vào các View mới
            uvText.setText(String.format(Locale.getDefault(), "%.0f", uvi));
            sunSetText.setText(getFormattedTime(sunsetTime)); // Dùng hàm mới
            pressureText.setText(String.format(Locale.getDefault(), "%.0f mbar", pressure));
            feelsLikeText.setText(String.format(Locale.getDefault(), "%.0f°", feelsLike));
            windDirection.setText(getWindDirection(windDeg)); // Dùng hàm mới
            // --- KẾT THÚC THÊM MỚI ---

            // --- Cập nhật dự báo HÀNG NGÀY ---
            if (jsonObject.has("daily")) {
                JSONArray dailyArray = jsonObject.getJSONArray("daily");
                updateDailyForecast(dailyArray);
            }

            // --- Cập nhật dự báo HÀNG GIỜ ---
            if (jsonObject.has("hourly")) {
                JSONArray hourlyArray = jsonObject.getJSONArray("hourly");
                updateHourlyForecast(hourlyArray);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi phân tích dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Hàm tìm và gán các View cho dự báo hàng ngày ---
    private void initDailyForecastViews(View view) {
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
    }


    // --- Hàm để cập nhật UI dự báo 5 ngày ---
    private void updateDailyForecast(JSONArray dailyArray) throws JSONException {
        int daysToDisplay = Math.min(dailyArray.length(), 5);

        for (int i = 0; i < daysToDisplay; i++) {
            JSONObject dayForecast = dailyArray.getJSONObject(i);

            long dt = dayForecast.getLong("dt");
            if (i == 0) {
                dailyDateViews[i].setText("Today");
            } else if (i == 1) {
                dailyDateViews[i].setText("Tomorrow");
            } else {
                dailyDateViews[i].setText(getDayOfWeek(dt));
            }

            JSONObject tempObject = dayForecast.getJSONObject("temp");
            double minTemp = tempObject.getDouble("min");
            double maxTemp = tempObject.getDouble("max");

            dailyMinTempViews[i].setText(String.format(Locale.getDefault(), "%.0f°", minTemp));
            dailyMaxTempViews[i].setText(String.format(Locale.getDefault(), "%.0f°", maxTemp));

            JSONObject weatherObject = dayForecast.getJSONArray("weather").getJSONObject(0);
            String iconCode = weatherObject.getString("icon");
            dailyIconViews[i].setImageResource(getIconResourceId(iconCode));
        }
    }

    // --- Hàm hỗ trợ đổi timestamp (giây) sang tên ngày ---
    private String getDayOfWeek(long timeStamp) {
        Date date = new Date(timeStamp * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(date);
    }

    // --- Hàm hỗ trợ đổi mã icon API sang ID drawable ---
    private int getIconResourceId(String iconCode) {
        String resourceName = "ic_" + iconCode;
        if (getContext() == null) {
            return R.drawable.ic_01d;
        }
        int resId = getContext().getResources().getIdentifier(
                resourceName,
                "drawable",
                getContext().getPackageName()
        );
        return (resId != 0) ? resId : R.drawable.ic_01d;
    }

    // --- Hàm cập nhật dự báo HÀNG GIỜ ---
    private void updateHourlyForecast(JSONArray hourlyArray) throws JSONException {
        List<HourlyForecast> newHourlyList = new ArrayList<>();
        int hoursToDisplay = Math.min(hourlyArray.length(), 24);

        for (int i = 0; i < hoursToDisplay; i++) {
            JSONObject hourForecast = hourlyArray.getJSONObject(i);

            long dt = hourForecast.getLong("dt");
            String time = getFormattedHour(dt, i == 0);

            double temp = hourForecast.getDouble("temp");
            String temperature = String.format(Locale.getDefault(), "%.0f°", temp);

            double windSpeed = hourForecast.getDouble("wind_speed"); // Đây là m/s
            // --- SỬA ĐỔI: Chuyển m/s sang km/h (nhân 3.6) ---
            String wind = String.format(Locale.getDefault(), "%.1f km/h", windSpeed * 3.6);

            JSONObject weatherObject = hourForecast.getJSONArray("weather").getJSONObject(0);
            String iconCode = weatherObject.getString("icon");
            int iconResId = getIconResourceId(iconCode);

            newHourlyList.add(new HourlyForecast(time, temperature, wind, iconResId));
        }

        if (hourlyForecasts != null && hourlyAdapter != null) {
            hourlyForecasts.clear();
            hourlyForecasts.addAll(newHourlyList);
            hourlyAdapter.notifyDataSetChanged();
        }
    }

    // --- Hàm hỗ trợ đổi timestamp (giây) sang định dạng giờ (ví dụ: "Now", "3 PM") ---
    private String getFormattedHour(long timeStamp, boolean isFirst) {
        if (isFirst) {
            return "Now";
        }
        Date date = new Date(timeStamp * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("h a", Locale.getDefault());
        return sdf.format(date);
    }

    // --- THÊM MỚI: Các hàm hỗ trợ cho chi tiết thời tiết ---

    /**
     * HÀM MỚI (Hỗ trợ): Đổi timestamp (giây) sang giờ:phút (ví dụ: "17:24").
     * @param timeStamp Thời gian từ API (tính bằng giây)
     * @return String đã định dạng (ví dụ: "17:24")
     */
    private String getFormattedTime(long timeStamp) {
        // Chuyển đổi giây sang mili giây
        Date date = new Date(timeStamp * 1000L);
        // Định dạng "HH:mm" trả về 24h (ví dụ: "17:24")
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * HÀM MỚI (Hỗ trợ): Đổi độ gió (0-360) sang hướng (ví dụ: "Đông Bắc").
     * @param degrees Độ gió từ API
     * @return String hướng gió
     */
    private String getWindDirection(double degrees) {
        String[] directions = {"Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc"};
        // Chia 360 độ thành 8 hướng (mỗi hướng 45 độ)
        int index = (int)Math.round(degrees / 45.0) % 8;
        return directions[index];
    }
    // --- KẾT THÚC THÊM MỚI ---
}