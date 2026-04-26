package com.yay.tripsync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private Context context;
    private List<Trip> trips;
    private int[] tripBackgrounds = {
            R.drawable.view1,
            R.drawable.view2,
            R.drawable.view3,
            R.drawable.view4,
            R.drawable.view5,
            R.drawable.view6
    };

    private SimpleDateFormat dateFormat = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());

    public TripAdapter(Context context, List<Trip> trips) {
        this.context = context;
        this.trips = trips;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.card_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);

        holder.tripName.setText(trip.getName());
        holder.tripLocation.setText(trip.getLocation());
        holder.tripDate.setText(trip.getStartDate() + " – " + trip.getEndDate());

        if (trip.getTripCode() != null && !trip.getTripCode().isEmpty()) {
            holder.tripCode.setText(trip.getTripCode());
            holder.tripCode.setVisibility(View.VISIBLE);
        } else {
            holder.tripCode.setVisibility(View.GONE);
        }

        updateStatusBadge(holder.tripStatus, trip.getStartDate(), trip.getEndDate());

        if (trip.getBudget() > 0) {
            holder.tripMoney.setVisibility(View.VISIBLE);
            holder.tripMoney.setText("Spent : ₹ " + (int)trip.getSpent() + " / ₹ " + (int)trip.getBudget());
        } else {
            holder.tripMoney.setVisibility(View.GONE);
        }

        int imageIndex = position % tripBackgrounds.length;
        holder.tripImage.setImageResource(tripBackgrounds[imageIndex]);

        if (trip.getImageUrl() != null && !trip.getImageUrl().isEmpty()) {
            Glide.with(context).load(trip.getImageUrl()).into(holder.tripImage);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TripDetailActivity.class);
            intent.putExtra("tripId", trip.getTripCode());
            intent.putExtra("name", trip.getName());
            intent.putExtra("location", trip.getLocation());
            intent.putExtra("startDate", trip.getStartDate());
            intent.putExtra("endDate", trip.getEndDate());
            intent.putExtra("budget", trip.getBudget());
            intent.putExtra("spent", trip.getSpent());
            intent.putExtra("imageRes", tripBackgrounds[imageIndex]);
            intent.putExtra("imageUrl", trip.getImageUrl());
            v.getContext().startActivity(intent);
        });

        // 🔥 Long click to hide trip for current user
        holder.itemView.setOnLongClickListener(v -> {
            showDeleteDialog(trip.getTripCode(), trip.getName(), position);
            return true;
        });
    }

    private void showDeleteDialog(String tripCode, String tripName, int position) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnDelete = dialogView.findViewById(R.id.btnDelete);

        tvTitle.setText("Remove Trip");
        tvMessage.setText("Are you sure you want to remove '" + tripName + "' from your list? Others will still be able to see it.");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnDelete.setOnClickListener(v -> {
            hideTripFromBackend(tripCode, position);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void hideTripFromBackend(String tripCode, int position) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("trips").whereEqualTo("tripCode", tripCode).get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (!queryDocumentSnapshots.isEmpty()) {
                queryDocumentSnapshots.getDocuments().get(0).getReference()
                        .update("hiddenBy", FieldValue.arrayUnion(currentUid))
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Trip removed from your list", Toast.LENGTH_SHORT).show();
                            // Note: The UI will refresh when the activity re-queries Firestore
                        })
                        .addOnFailureListener(e -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateStatusBadge(TextView statusTextView, String startDateStr, String endDateStr) {
        try {
            Date today = Calendar.getInstance().getTime();
            Calendar cal = Calendar.getInstance();
            cal.setTime(today);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            today = cal.getTime();

            Date startDate = dateFormat.parse(startDateStr);
            Date endDate = dateFormat.parse(endDateStr);

            String status;
            int color;

            if (today.before(startDate)) {
                status = "Upcoming";
                color = Color.parseColor("#FF5C8A");
            } else if (today.after(endDate)) {
                status = "Completed";
                color = Color.parseColor("#888888");
            } else {
                status = "Ongoing";
                color = Color.parseColor("#2196F3");
            }

            statusTextView.setText(status);
            GradientDrawable background = (GradientDrawable) statusTextView.getBackground();
            if (background != null) {
                background.setColor(color);
            }

        } catch (ParseException | NullPointerException e) {
            statusTextView.setText("Upcoming");
        }
    }

    @Override
    public int getItemCount() { return trips.size(); }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        ImageView tripImage;
        TextView tripName, tripLocation, tripDate, tripStatus, tripMoney, tripCode;

        TripViewHolder(View itemView) {
            super(itemView);
            tripImage = itemView.findViewById(R.id.tripImage);
            tripName = itemView.findViewById(R.id.tripName);
            tripLocation = itemView.findViewById(R.id.tripLocation);
            tripDate = itemView.findViewById(R.id.tripDate);
            tripStatus = itemView.findViewById(R.id.tripStatus);
            tripMoney = itemView.findViewById(R.id.tripMoney);
            tripCode = itemView.findViewById(R.id.tvTripCode);
        }
    }
}