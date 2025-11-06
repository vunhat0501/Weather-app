package com.example.weatherapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherapp.R;
import com.example.weatherapp.hourlyforecast.HourlyForecast;

import java.util.List;

public class HourlyForecastAdapter extends RecyclerView.Adapter<HourlyForecastAdapter.ForecastViewHolder> {

    private List<HourlyForecast> forecastList;

    // Constructor to get the data
    public HourlyForecastAdapter(List<HourlyForecast> forecastList) {
        this.forecastList = forecastList;
    }

    // This creates the new View object (inflates the item layout)
    @NonNull
    @Override
    public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly_forecast, parent, false);
        return new ForecastViewHolder(view);
    }

    // This binds the data from your list to the views in the layout
    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
        HourlyForecast forecast = forecastList.get(position);

        holder.timeText.setText(forecast.getTime());
        holder.tempText.setText(forecast.getTemperature());
        holder.windText.setText(forecast.getWindSpeed());
        holder.iconImage.setImageResource(forecast.getWeatherIconResId());
    }

    // This tells the RecyclerView how many items are in the list
    @Override
    public int getItemCount() {
        return forecastList.size();
    }

    // This class holds the references to the views in your item layout
    public static class ForecastViewHolder extends RecyclerView.ViewHolder {
        TextView timeText;
        TextView tempText;
        TextView windText;
        ImageView iconImage;

        public ForecastViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.hourly_time);
            tempText = itemView.findViewById(R.id.hourly_temp);
            windText = itemView.findViewById(R.id.hourly_wind);
            iconImage = itemView.findViewById(R.id.hourly_icon);
        }
    }
}