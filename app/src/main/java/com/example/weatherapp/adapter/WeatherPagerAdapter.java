package com.example.weatherapp.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.weatherapp.fragments.WeatherFragment;

public class WeatherPagerAdapter extends FragmentStateAdapter {

    public WeatherPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Create the fragment based on its position
        if (position == 0) {
            // Page 0: Manual City Input
            return WeatherFragment.newInstance(false);
        } else {
            // Page 1: GPS Mode
            return WeatherFragment.newInstance(true);
        }
    }

    @Override
    public int getItemCount() {
        return 2; // We have two screens
    }
}