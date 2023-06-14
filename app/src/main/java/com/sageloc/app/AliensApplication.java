package com.sageloc.app;

import android.app.Application;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

/**
 * This is a subclass of {@link Application} used to provide shared objects for this app, such as
 * the {@link Tracker}.
 *
 * Initializes:
 *      Analytics Tracker
 *      Logging
 */
public class AliensApplication extends Application {

    public static boolean SENSOR_TESTING_MODE = true;


    private Tracker mTracker;

    /**
     * Gets the default {@link Tracker} for this {@link Application}.
     * @return tracker
     */
    synchronized public Tracker getDefaultTracker() {
        if (mTracker == null) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
            // To enable debug logging use: adb shell setprop log.tag.GAv4 DEBUG
            mTracker = analytics.newTracker(R.xml.analytics_tracker_configuration);
        }
        return mTracker;
    }

    public void onCreate() {
        super.onCreate();

        AliensAppLogger.Init();

    }
}
