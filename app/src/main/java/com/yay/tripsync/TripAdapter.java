package com.yay.tripsync;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private Context context;
    private List<Trip> trips;

    public TripAdapter(Context context, List<Trip> trips) {
        this.context = context;
        this.trips = trips;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);

        holder.tripName.setText(trip.getName());
        holder.tripLocation.setText("📍 " + trip.getLocation());
        holder.tripDates.setText("🗓 " + trip.getStartDate() + " – " + trip.getEndDate());

        // Status badge
        if (trip.getStatus() != null) {
            holder.statusBadge.setText(trip.getStatus());
            switch (trip.getStatus()) {
                case "Upcoming":
                    holder.statusBadge.setBackgroundColor(Color.parseColor("#4CAF50"));
                    break;
                case "Ongoing":
                    holder.statusBadge.setBackgroundColor(Color.parseColor("#2196F3"));
                    break;
                default:
                    holder.statusBadge.setBackgroundColor(Color.parseColor("#9E9E9E"));
            }
        }

        // Spent
        if (trip.getBudget() > 0) {
            holder.spentText.setVisibility(View.VISIBLE);
            holder.spentText.setText("Spent : ₹ " + (int)trip.getSpent() + " / ₹ " + (int)trip.getBudget());
        } else {
            holder.spentText.setVisibility(View.GONE);
        }

        // Image
        if (trip.getImageUrl() != null && !trip.getImageUrl().isEmpty()) {
            Glide.with(context).load(trip.getImageUrl()).into(holder.tripImage);
        }
    }

    @Override
    public int getItemCount() { return trips.size(); }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        ImageView tripImage;
        TextView tripName, tripLocation, tripDates, statusBadge, spentText;

        TripViewHolder(View itemView) {
            super(itemView);
            tripImage    = itemView.findViewById(R.id.tripImage);
            tripName     = itemView.findViewById(R.id.tripName);
            tripLocation = itemView.findViewById(R.id.tripLocation);
            tripDates    = itemView.findViewById(R.id.tripDates);
            statusBadge  = itemView.findViewById(R.id.statusBadge);
            spentText    = itemView.findViewById(R.id.spentText);
        }
    }
}