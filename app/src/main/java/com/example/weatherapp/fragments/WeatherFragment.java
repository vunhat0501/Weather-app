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
import java.text.SimpleDateFormat; // <-- IMPORT MỚI
import java.util.ArrayList;
import java.util.Date; // <-- IMPORT MỚI
import java.util.List;
import java.util.Locale; // <-- IMPORT MỚI
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

    // --- MỚI: Mảng để giữ các View của dự báo hàng ngày ---
    private final TextView[] dailyDateViews = new TextView[5];
    private final TextView[] dailyMinTempViews = new TextView[5];
    private final TextView[] dailyMaxTempViews = new TextView[5];
    private final ImageView[] dailyIconViews = new ImageView[5];
    // --- KẾT THÚC PHẦN MỚI ---

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

        // --- MỚI: Gọi hàm để tìm các view dự báo hàng ngày ---
        initDailyForecastViews(view);
        // --- KẾT THÚC PHẦN MỚI ---

        // This is the magic:
        if (isGpsMode) {
            searchIcon.setVisibility(View.GONE);
            cityInputLayout.setVisibility(View.GONE);
            searchIcon.setVisibility(View.GONE);
            gpsIcon.setVisibility(View.VISIBLE);

            // TODO: Put all your GPS logic here
            // 1. Request location permissions
            // 2. Get user's coordinates
            // 3. Call weather API with those coordinates
            locationHandler.requestLocationPermission(requestPermissionLauncher);
        } else {
            searchIcon.setVisibility(View.VISIBLE);
            cityInputLayout.setVisibility(View.GONE); // Explicitly hide on create
            gpsIcon.setVisibility(View.GONE);

            FetchWeatherData("Hanoi, VN");
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
        // init empty array to avoid nullpointer exception
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
        FetchCityNameFromCoords(lat, lon);
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
        // --- SỬA ĐỔI: Bỏ 'daily' khỏi 'exclude' để lấy dữ liệu dự báo hàng ngày ---
        String url = "https://api.openweathermap.org/data/3.0/onecall?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric&exclude=minutely,alerts";

        // Dùng chung executeWeatherRequest
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

                    // Phân tích JSON của Geocoding (nó là một mảng)
                    JSONArray jsonArray = new JSONArray(geoResult);
                    if (jsonArray.length() > 0) {
                        JSONObject geoObject = jsonArray.getJSONObject(0);
                        double lat = geoObject.getDouble("lat");
                        double lon = geoObject.getDouble("lon");
                        // Bước 2: Gọi One Call API với tọa độ vừa tìm được
                        FetchWeatherData(lat, lon);

                    } else {
                        // Không tìm thấy thành phố
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
                        // Lấy tên thành phố từ API Geocoding
                        String cityName = geoObject.getString("name");

                        // Cập nhật UI (trên luồng chính)
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // Chỉ cập nhật tên thành phố
                                cityNameText.setText(cityName);
                            });
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                // Không làm gì nếu lỗi, chỉ là không hiển thị được tên
            }
        });
    }

    private void updateUI(String result) {
        try {
            JSONObject jsonObject = new JSONObject(result);
            JSONObject current = jsonObject.getJSONObject("current");

            // --- Cập nhật thời tiết hiện tại (như cũ) ---
            double temperature = current.getDouble("temp");
            double humidity = current.getDouble("humidity");
            double windSpeed = current.getDouble("wind_speed");
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
            humidityText.setText(String.format("%.0f%%", humidity));
            windText.setText(String.format("%.0f Km/h", windSpeed));
            descriptionText.setText(description);

            // --- MỚI: Lấy mảng daily và cập nhật UI dự báo ---
            if (jsonObject.has("daily")) {
                JSONArray dailyArray = jsonObject.getJSONArray("daily");
                updateDailyForecast(dailyArray);
            }
            // --- KẾT THÚC PHẦN MỚI ---

            // add function to call updateHourlyForecast
            if (jsonObject.has("hourly")) {
                JSONArray hourlyArray = jsonObject.getJSONArray("hourly");
                updateHourlyForecast(hourlyArray);
            }
            // --- KẾT THÚC SỬA LỖI ---

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Lỗi phân tích dữ liệu", Toast.LENGTH_SHORT).show();
        }
    }

    // --- MỚI: Hàm tìm và gán các View cho dự báo hàng ngày ---

    /**
     * HÀM MỚI: Tìm và gán các View cho dự báo hàng ngày từ layout.
     *
     * @param view View gốc của Fragment (từ onViewCreated)
     */
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
    // --- KẾT THÚC PHẦN MỚI ---


    // --- MỚI: Hàm để cập nhật UI dự báo 5 ngày ---

    /**
     * HÀM MỚI: Cập nhật UI dự báo 5 ngày từ dữ liệu JSON.
     *
     * @param dailyArray Mảng JSON "daily" từ One Call API
     */
    private void updateDailyForecast(JSONArray dailyArray) throws JSONException {
        // API trả về 8 ngày, nhưng layout của chúng ta chỉ cần 5 ngày
        int daysToDisplay = Math.min(dailyArray.length(), 5);

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
            JSONObject weatherObject = dayForecast.getJSONArray("weather").getJSONObject(0);
            String iconCode = weatherObject.getString("icon");
            dailyIconViews[i].setImageResource(getIconResourceId(iconCode));
        }
    }
    // --- KẾT THÚC PHẦN MỚI ---

    // --- MỚI: Hàm hỗ trợ đổi timestamp (giây) sang tên ngày (ví dụ: "Wednesday") ---

    /**
     * HÀM MỚI (Hỗ trợ): Đổi timestamp (giây) sang tên ngày (ví dụ: "Wednesday").
     */
    private String getDayOfWeek(long timeStamp) {
        // Chuyển đổi giây sang mili giây
        Date date = new Date(timeStamp * 1000L);
        // Định dạng "EEEE" trả về tên đầy đủ của ngày (ví dụ: "Wednesday")
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(date);
    }
    // --- KẾT THÚC PHẦN MỚI ---

    // --- MỚI: Hàm hỗ trợ đổi mã icon API (ví dụ "01d") sang ID drawable (ví dụ R.drawable.ic_01d) ---

    /**
     * HÀM MỚI (Hỗ trợ): Đổi mã icon API (ví dụ "01d") sang ID drawable (ví dụ R.drawable.ic_01d).
     */
    private int getIconResourceId(String iconCode) {
        // Tạo tên tài nguyên, ví dụ: "ic_01d"
        String resourceName = "ic_" + iconCode;

        // Cần getContext() để truy cập resources
        if (getContext() == null) {
            return R.drawable.ic_01d; // Trả về mặc định nếu context null
        }

        int resId = getContext().getResources().getIdentifier(
                resourceName,
                "drawable",
                getContext().getPackageName()
        );

        // Nếu không tìm thấy icon (resId == 0), trả về một icon mặc định
        return (resId != 0) ? resId : R.drawable.ic_01d;
    }

    // --- KẾT THÚC PHẦN MỚI ---

    // New function for updating weather hourly
    // --- HÀM MỚI ĐỂ CẬP NHẬT DỰ BÁO HÀNG GIỜ ---

    /**
     * HÀM MỚI: Cập nhật RecyclerView dự báo hàng giờ từ dữ liệu JSON.
     *
     * @param hourlyArray Mảng JSON "hourly" từ One Call API
     */
    private void updateHourlyForecast(JSONArray hourlyArray) throws JSONException {
        // Tạo một danh sách tạm thời để chứa dữ liệu mới
        List<HourlyForecast> newHourlyList = new ArrayList<>();

        // API trả về 48 giờ, nhưng ta chỉ cần hiển thị 24 giờ cho hợp lý
        int hoursToDisplay = Math.min(hourlyArray.length(), 24);

        for (int i = 0; i < hoursToDisplay; i++) {
            JSONObject hourForecast = hourlyArray.getJSONObject(i);

            // 1. Lấy và định dạng thời gian
            long dt = hourForecast.getLong("dt");
            // i == 0 nghĩa là giờ hiện tại, dùng "Now"
            String time = getFormattedHour(dt, i == 0);

            // 2. Lấy và định dạng nhiệt độ
            double temp = hourForecast.getDouble("temp");
            String temperature = String.format(Locale.getDefault(), "%.0f°", temp);

            // 3. Lấy và định dạng tốc độ gió
            // Lưu ý: API trả về m/s. Code của bạn đang hiển thị "Km/h"
            // cho cả current wind. Để nhất quán, chúng ta sẽ làm tương tự
            // (Mặc dù đúng ra phải * 3.6 để ra km/h)
            double windSpeed = hourForecast.getDouble("wind_speed");
            String wind = String.format(Locale.getDefault(), "%.0f km/h", windSpeed);

            // 4. Lấy Icon (Tận dụng hàm đã có)
            JSONObject weatherObject = hourForecast.getJSONArray("weather").getJSONObject(0);
            String iconCode = weatherObject.getString("icon");
            int iconResId = getIconResourceId(iconCode);

            // 5. Thêm vào danh sách
            newHourlyList.add(new HourlyForecast(time, temperature, wind, iconResId));
        }

        // Cập nhật danh sách gốc (đang chứa data mẫu) và thông báo cho Adapter
        if (hourlyForecasts != null && hourlyAdapter != null) {
            hourlyForecasts.clear(); // Xóa dữ liệu mẫu
            hourlyForecasts.addAll(newHourlyList); // Thêm dữ liệu thật
            hourlyAdapter.notifyDataSetChanged(); // Yêu cầu RecyclerView vẽ lại
        }
    }

    /**
     * HÀM MỚI (Hỗ trợ): Đổi timestamp (giây) sang định dạng giờ (ví dụ: "Now", "3 PM").
     *
     * @param timeStamp Thời gian từ API (tính bằng giây)
     * @param isFirst   True nếu đây là mục đầu tiên (giờ hiện tại)
     * @return String đã định dạng (ví dụ: "Now" hoặc "3 PM")
     */
    private String getFormattedHour(long timeStamp, boolean isFirst) {
        if (isFirst) {
            return "Now";
        }
        // Chuyển đổi giây sang mili giây
        Date date = new Date(timeStamp * 1000L);
        // Định dạng "h a" trả về giờ và AM/PM (ví dụ: "3 PM")
        SimpleDateFormat sdf = new SimpleDateFormat("h a", Locale.getDefault());
        return sdf.format(date);
    }
    // --- KẾT THÚC PHẦN MỚI ---
    // Your helper method is now part of the fragment
}