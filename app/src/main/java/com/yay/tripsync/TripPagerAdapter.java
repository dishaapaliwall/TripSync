package com.yay.tripsync;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.yay.tripsync.ChatFragment;

public class TripPagerAdapter extends FragmentStateAdapter {

    private String tripId;

    public TripPagerAdapter(@NonNull FragmentActivity fragmentActivity, String tripId) {
        super(fragmentActivity);
        this.tripId = tripId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new ItineraryFragment();
            case 1: return new ExpensesFragment();
            case 2: return new ChecklistFragment();
            case 3: return new MembersFragment();
            case 4: return new PhotosFragment();
            case 5: return ChatFragment.newInstance(tripId);
            case 6: return TrackFragment.newInstance(tripId);
            default: return new ItineraryFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 7;
    }
}