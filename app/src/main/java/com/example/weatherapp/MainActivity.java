package com.example.weatherapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.weatherapp.adapter.WeatherPagerAdapter;

public class MainActivity extends AppCompatActivity {

    // Removed all the RecyclerView variables

    private ViewPager2 viewPager;
    private WeatherPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. Set the content view to your NEW layout file
        setContentView(R.layout.activity_main); // This is NOT fragment_weather

        // 2. Find the ViewPager2
        viewPager = findViewById(R.id.viewPager);

        // 3. Set up the adapter
        pagerAdapter = new WeatherPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Set gps as the default page
        viewPager.setCurrentItem(1);
    }

    // 4. All the other methods (like createSampleData) are REMOVED from this file.
    // They will be moved to WeatherFragment.java.
}