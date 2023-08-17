/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.carlauncher;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.app.ActivityView;
import android.car.app.CarActivityView;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.android.car.media.common.PlaybackFragment;

/**
  * A basic launcher for Android Automotive that demonstrates how to use {@link ActivityView} to host map content.
  *
  * <p>Note: On some devices, the ActivityView's rendered width, height, and/or aspect ratio may not meet the Android Compatibility Definition.
  * Developers should work with content owners to ensure proper rendering of content when extending or mocking this class.
  *
  * <p>Note: Since the hosted maps activity in the ActivityView is currently on the virtual screen, the system considers the activity to always be in front.
  * Launching the Maps activity with a direct intent will not work.
  * To start the "Maps" activity on the real display, use the {@link Intent#CATEGORY_APP_MAPS} category to send the intent to the launcher,
  * The launcher will start the activity on the real display.
  *
  * <p>Note: When switching from or back to the current user, the status of the virtual display in ActivityView is uncertain.
  * To avoid crashes, this activity will be done when switching users.
  */
public class CarLauncher extends FragmentActivity {
    private static final String TAG = "CarLauncher";
    private static final boolean DEBUG = false;

    private CarActivityView mActivityView;
    private boolean mActivityViewReady;
    private boolean mIsStarted;
    private DisplayManager mDisplayManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Set to {@code true} once we record that the activity has been fully drawn.
     */
    private boolean mIsReadyLogged;

    private final ActivityView.StateCallback mActivityViewCallback = new ActivityView.StateCallback() {
        @Override
        public void onActivityViewReady(ActivityView view) {
            if (DEBUG) Log.d(TAG, "onActivityViewReady(" + getUserId() + ")");
            mActivityViewReady = true;
            startMapsInActivityView();
            maybeLogReady();
        }

        @Override
        public void onActivityViewDestroyed(ActivityView view) {
            if (DEBUG) Log.d(TAG, "onActivityViewDestroyed(" + getUserId() + ")");
            mActivityViewReady = false;
        }

        @Override
        public void onTaskMovedToFront(int taskId) {
            if (DEBUG) {
                Log.d(TAG, "onTaskMovedToFront(" + getUserId() + "): started=" + mIsStarted);
            }
            try {
                if (mIsStarted) {
                    ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    am.moveTaskToFront(CarLauncher.this.getTaskId(), /* flags= */ 0);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to move CarLauncher to front.");
            }
        }
    };

    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != getDisplay().getDisplayId()) {
                return;
            }
            // startMapsInActivityView() will check Display's State.
            startMapsInActivityView();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
      // Do not show the Map panel in multi-window mode.
         // Note: CTS tests for split screen are not compatible with the activity view of the launcher's default activity
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            setContentView(R.layout.car_launcher_multiwindow);
        } else {
            setContentView(R.layout.car_launcher);
        }
        initializeFragments();
        mActivityView = findViewById(R.id.maps);
        if (mActivityView != null) {
            mActivityView.setCallback(mActivityViewCallback);
        }
        mDisplayManager = getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, mMainHandler);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startMapsInActivityView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsStarted = true;
        maybeLogReady();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsStarted = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        if (mActivityView != null && mActivityViewReady) {
            mActivityView.release();
        }
    }

    private void startMapsInActivityView() {
        if (mActivityView == null || !mActivityViewReady) {
            return;
        }
        // If we happen to be re-rendered into multi-display mode, we'll skip launching content in the Activity view, since we'll be re-created anyway.
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            return;
        }
        // Do not start the map when the display of the "ActivityVisibilityTests" is off.
        if (getDisplay().getState() != Display.STATE_ON) {
            return;
        }
        try {
            mActivityView.startActivity(getMapsIntent());
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Maps activity not found", e);
        }
    }

    private Intent getMapsIntent() {
        // Create an intent for your application's main activity that doesn't specify a specific activity to run, but instead provides a selector to find that activity.
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initializeFragments();
    }

    private void initializeFragments() {
        PlaybackFragment playbackFragment = new PlaybackFragment();
        ContextualFragment contextualFragment = null;
        FrameLayout contextual = findViewById(R.id.contextual);
        if (contextual != null) {
            contextualFragment = new ContextualFragment();
        }

        FragmentTransaction fragmentTransaction =
                getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playback, playbackFragment);
        if (contextual != null) {
            fragmentTransaction.replace(R.id.contextual, contextualFragment);
        }
        fragmentTransaction.commitNow();
    }

    /**
   * Recording activity is ready. Used for boot time diagnostics.
     */
    private void maybeLogReady() {
        if (DEBUG) {
            Log.d(TAG, "maybeLogReady(" + getUserId() + "): activityReady=" + mActivityViewReady
                    + ", started=" + mIsStarted + ", alreadyLogged: " + mIsReadyLogged);
        }
        if (mActivityViewReady && mIsStarted) {
          // We should report every time - the Android framework will handle logging the first time the log is effectively drawn, however. . . .
            reportFullyDrawn();
            if (!mIsReadyLogged) {
                // ... we want to manually check that the Log.i below (which is useful to show
                // the user id) is only logged once (otherwise it would be logged everytime the user
                // taps Home)
                Log.i(TAG, "Launcher for user " + getUserId() + " is ready");
                mIsReadyLogged = true;
            }
        }
    }
}
