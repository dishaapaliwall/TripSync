package com.yay.tripsync;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import java.util.HashMap;
import java.util.Map;

public class TripSyncApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Cloudinary Initialization
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "dsuwxepmx");
        config.put("secure", true); // Ensures https is used
        MediaManager.init(this, config);
    }
}
