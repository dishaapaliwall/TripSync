package com.yay.tripsync;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackFragment extends Fragment {

    private TextView tvMeetupName, tvLocationSubtitle;
    private Button btnSetDestination;
    private SwitchCompat locationToggle;
    private RecyclerView rvMemberDistances;
    private String tripId, actualDocId;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private com.google.firebase.firestore.ListenerRegistration locationListener, selfLocationListener, tripListener;
    private MemberDistanceAdapter adapter;
    private List<Map<String, Object>> memberList = new ArrayList<>();
    private double meetupLat = 0, meetupLng = 0;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                
                boolean granted = (fineLocationGranted != null && fineLocationGranted) || 
                                 (coarseLocationGranted != null && coarseLocationGranted);
                
                if (granted) {
                    if (isLocationEnabled()) {
                        startLocationSharing();
                    } else {
                        Toast.makeText(getContext(), "Please enable device location", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        locationToggle.setChecked(false);
                    }
                } else {
                    locationToggle.setChecked(false);
                    Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            }
    );

    public static TrackFragment newInstance(String tripId) {
        TrackFragment fragment = new TrackFragment();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
        }
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_track, container, false);

        tvMeetupName = view.findViewById(R.id.tvMeetupName);
        tvLocationSubtitle = view.findViewById(R.id.tvLocationSubtitle);
        btnSetDestination = view.findViewById(R.id.btnSetDestination);
        locationToggle = view.findViewById(R.id.locationToggle);
        rvMemberDistances = view.findViewById(R.id.rvMemberDistances);

        rvMemberDistances.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MemberDistanceAdapter();
        rvMemberDistances.setAdapter(adapter);

        btnSetDestination.setOnClickListener(v -> showDestinationDialog());

        locationToggle.setOnClickListener(v -> {
            boolean isChecked = locationToggle.isChecked();
            if (isChecked) {
                checkPermissionAndStartLocation();
            } else {
                stopLocationSharing();
            }
        });

        if (tripId != null) {
            loadTripAndMembers();
        }

        return view;
    }

    private void checkPermissionAndStartLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(getContext(), "Please enable device location", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            locationToggle.setChecked(false);
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationSharing();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {}

        return gpsEnabled || networkEnabled;
    }

    private void startLocationSharing() {
        if (actualDocId == null) {
            return;
        }
        
        tvLocationSubtitle.setText("Sharing On");
        updateSharingStatus(true);

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateUserLocation(location);
                }
            }
        };

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationSharing() {
        tvLocationSubtitle.setText("Off");
        updateSharingStatus(false);
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void updateSharingStatus(boolean isSharing) {
        if (auth.getUid() == null || actualDocId == null) return;
        String uid = auth.getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("sharing", isSharing);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("trips").document(actualDocId)
                .collection("memberLocations").document(uid)
                .update(updates)
                .addOnFailureListener(e -> {
                    if (isSharing) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("name", auth.getCurrentUser().getDisplayName() != null ? auth.getCurrentUser().getDisplayName() : auth.getCurrentUser().getEmail());
                        data.put("sharing", true);
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        data.put("lat", 0.0);
                        data.put("lng", 0.0);
                        db.collection("trips").document(actualDocId)
                                .collection("memberLocations").document(uid).set(data);
                    }
                });
    }

    private void updateUserLocation(Location location) {
        if (auth.getUid() == null || actualDocId == null) return;
        String uid = auth.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("lat", location.getLatitude());
        data.put("lng", location.getLongitude());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("sharing", true);
        data.put("name", auth.getCurrentUser().getDisplayName() != null ? auth.getCurrentUser().getDisplayName() : auth.getCurrentUser().getEmail());

        db.collection("trips").document(actualDocId)
                .collection("memberLocations").document(uid)
                .set(data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (locationListener != null) {
            locationListener.remove();
        }
        if (selfLocationListener != null) {
            selfLocationListener.remove();
        }
        if (tripListener != null) {
            tripListener.remove();
        }
    }

    private void showDestinationDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_set_destination, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText etInput = dialogView.findViewById(R.id.etDestinationName);
        View btnCurrentLoc = dialogView.findViewById(R.id.btnCurrentLocation);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnSet = dialogView.findViewById(R.id.btnSet);

        btnCurrentLoc.setOnClickListener(v -> {
            useCurrentLocationAsMeetup();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSet.setOnClickListener(v -> {
            String destinationName = etInput.getText().toString().trim();
            if (!destinationName.isEmpty()) {
                geocodeAndSave(destinationName);
                dialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Please enter a location name", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void useCurrentLocationAsMeetup() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        String addressName = "Live Location";
                        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                addressName = addresses.get(0).getLocality();
                                if (addressName == null) addressName = addresses.get(0).getSubLocality();
                                if (addressName == null) addressName = "Current Point";
                            }
                        } catch (IOException ignored) {}
                        
                        saveMeetupPoint(addressName, location.getLatitude(), location.getLongitude(), "Live Location");
                    } else {
                        Toast.makeText(getContext(), "Could not get current location. Ensure GPS is on.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void geocodeAndSave(String destinationName) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(destinationName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                saveMeetupPoint(destinationName, address.getLatitude(), address.getLongitude(), address.getAddressLine(0));
            } else {
                Toast.makeText(getContext(), "Location coordinates not found. Saving name only.", Toast.LENGTH_SHORT).show();
                saveMeetupPoint(destinationName, 0.0, 0.0, "Address not found");
            }
        } catch (IOException e) {
            Log.e("TrackFragment", "Geocoding error", e);
            Toast.makeText(getContext(), "Geocoding failed. Saving name only.", Toast.LENGTH_SHORT).show();
            saveMeetupPoint(destinationName, 0.0, 0.0, "Service unavailable");
        }
    }

    private void saveMeetupPoint(String name, double lat, double lng, String address) {
        if (actualDocId == null) return;

        Map<String, Object> meetupPoint = new HashMap<>();
        meetupPoint.put("name", name);
        meetupPoint.put("address", address);
        meetupPoint.put("lat", lat);
        meetupPoint.put("lng", lng);
        meetupPoint.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("trips").document(actualDocId)
                .update("meetupPoint", meetupPoint)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Meetup Point Set!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void listenToMeetupPoint() {
        if (actualDocId == null) return;
        
        if (tripListener != null) tripListener.remove();
        tripListener = db.collection("trips").document(actualDocId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists() && snapshot.contains("meetupPoint")) {
                        Map<String, Object> meetup = (Map<String, Object>) snapshot.get("meetupPoint");
                        updateMeetupCoords(meetup);
                    }
                });
    }

    private void updateMeetupCoords(Map<String, Object> meetup) {
        if (meetup != null) {
            String name = (String) meetup.get("name");
            tvMeetupName.setText(name != null ? name : "No destination selected");
            
            Object latObj = meetup.get("lat");
            Object lngObj = meetup.get("lng");
            if (latObj instanceof Number) meetupLat = ((Number) latObj).doubleValue();
            if (lngObj instanceof Number) meetupLng = ((Number) lngObj).doubleValue();
            
            updateAllDistances();
        }
    }

    private void updateAllDistances() {
        for (int i = 0; i < memberList.size(); i++) {
            Map<String, Object> member = memberList.get(i);
            boolean sharing = false;
            Object sharingObj = member.get("sharing");
            if (sharingObj instanceof Boolean) sharing = (boolean) sharingObj;
            
            double lat = 0;
            Object latObj = member.get("lat");
            if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
            
            double lng = 0;
            Object lngObj = member.get("lng");
            if (lngObj instanceof Number) lng = ((Number) lngObj).doubleValue();
            
            member.put("status", formatDistance(sharing, lat, lng));
        }
        adapter.notifyDataSetChanged();
    }

    private void loadTripAndMembers() {
        db.collection("trips").document(tripId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                actualDocId = tripId;
                setupTripListeners();
            } else {
                db.collection("trips").whereEqualTo("tripCode", tripId).get().addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        actualDocId = querySnapshot.getDocuments().get(0).getId();
                        setupTripListeners();
                    }
                });
            }
        });
    }

    private void setupTripListeners() {
        if (actualDocId == null) return;
        
        listenToMeetupPoint();
        listenToSelfSharingStatus();

        db.collection("trips").document(actualDocId).addSnapshotListener((doc, error) -> {
            if (doc != null && doc.exists()) {
                processTripDoc(doc);
            }
        });
    }

    private void listenToSelfSharingStatus() {
        if (auth.getUid() == null || actualDocId == null) return;
        if (selfLocationListener != null) selfLocationListener.remove();

        selfLocationListener = db.collection("trips").document(actualDocId)
                .collection("memberLocations").document(auth.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        Boolean sharing = snapshot.getBoolean("sharing");
                        if (sharing != null) {
                            locationToggle.setChecked(sharing);
                            tvLocationSubtitle.setText(sharing ? "Sharing On" : "Off");
                            
                            if (sharing && locationCallback == null) {
                                startLocationSharing();
                            } else if (!sharing && locationCallback != null) {
                                stopLocationSharing();
                            }
                        }
                    }
                });
    }

    private void processTripDoc(DocumentSnapshot doc) {
        String tripOwnerId = doc.getString("userId");
        List<String> participants = (List<String>) doc.get("participants");
        if (participants == null) participants = new ArrayList<>();
        fetchUserDetails(participants, tripOwnerId);
    }

    private void fetchUserDetails(List<String> emails, String ownerId) {
        db.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            Map<String, DocumentSnapshot> foundUsers = new HashMap<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String email = doc.getString("email");
                if (email != null) foundUsers.put(email.toLowerCase().trim(), doc);
                foundUsers.put(doc.getId(), doc);
            }

            List<Map<String, Object>> newList = new ArrayList<>();
            DocumentSnapshot ownerDoc = foundUsers.get(ownerId);
            if (ownerDoc != null) addMemberToList(ownerDoc, newList);
            
            for (String email : emails) {
                DocumentSnapshot userDoc = foundUsers.get(email.toLowerCase().trim());
                if (userDoc != null && !userDoc.getId().equals(ownerId)) {
                    addMemberToList(userDoc, newList);
                }
            }

            memberList.clear();
            memberList.addAll(newList);
            adapter.notifyDataSetChanged();
            listenToMemberLocations();
        });
    }

    private void listenToMemberLocations() {
        if (actualDocId == null) return;
        if (locationListener != null) locationListener.remove();

        locationListener = db.collection("trips").document(actualDocId)
                .collection("memberLocations")
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String uid = doc.getId();
                        Boolean sharing = doc.getBoolean("sharing");
                        Double lat = doc.getDouble("lat");
                        Double lng = doc.getDouble("lng");
                        updateMemberStatusInList(uid, sharing != null && sharing, lat != null ? lat : 0.0, lng != null ? lng : 0.0);
                    }
                });
    }

    private void updateMemberStatusInList(String uid, boolean sharing, double lat, double lng) {
        for (int i = 0; i < memberList.size(); i++) {
            Map<String, Object> member = memberList.get(i);
            if (uid.equals(member.get("uid"))) {
                member.put("sharing", sharing);
                member.put("lat", lat);
                member.put("lng", lng);
                member.put("status", formatDistance(sharing, lat, lng));
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private String formatDistance(boolean sharing, double lat, double lng) {
        if (!sharing || (lat == 0 && lng == 0) || (meetupLat == 0 && meetupLng == 0)) {
            return "Off";
        }

        float[] results = new float[1];
        Location.distanceBetween(lat, lng, meetupLat, meetupLng, results);
        float distanceInMeters = results[0];

        if (distanceInMeters < 50) {
            return "Reached";
        } else if (distanceInMeters < 1000) {
            return (int) distanceInMeters + " m away";
        } else {
            return String.format(Locale.getDefault(), "%.1f km away", distanceInMeters / 1000.0);
        }
    }

    private void addMemberToList(DocumentSnapshot doc, List<Map<String, Object>> list) {
        Map<String, Object> member = new HashMap<>();
        member.put("uid", doc.getId());
        String name = doc.getString("name");
        if (name == null || name.isEmpty()) {
            String email = doc.getString("email");
            name = (email != null) ? email.split("@")[0] : "Unknown";
        }
        member.put("name", name);
        member.put("status", "Off");
        member.put("lat", 0.0);
        member.put("lng", 0.0);
        member.put("sharing", false);
        list.add(member);
    }

    private class MemberDistanceAdapter extends RecyclerView.Adapter<MemberDistanceAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member_distance, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> member = memberList.get(position);
            holder.tvName.setText((String) member.get("name"));
            holder.tvDistance.setText((String) member.get("status"));
        }

        @Override
        public int getItemCount() {
            return memberList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDistance;
            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.memberName);
                tvDistance = v.findViewById(R.id.memberDistance);
            }
        }
    }
}
