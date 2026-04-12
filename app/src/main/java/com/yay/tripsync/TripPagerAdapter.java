package com.yay.tripsync;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.yay.tripsync.ChatFragment;

public class TripPagerAdapter extends FragmentStateAdapter {

    public TripPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
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
            case 5: return new ChatFragment();
            default: return new ItineraryFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 6;
    }
}