/*
 * Copyright (C) 2014 Slimroms
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
package com.slim.device;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.service.notification.ZenModeConfig;
import android.os.UserHandle;
import com.slim.device.settings.ScreenOffGesture;
import com.slim.device.SliderSettings;
import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.aospextended.ActionConstants;
import com.android.internal.util.aospextended.Action;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = "KeyHandler";
    private static final int GESTURE_REQUEST = 1;
    private static final boolean DEBUG = false;

    // Supported scancodes
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int GESTURE_V_UP_SCANCODE = 255;
    // Slider
    private static final int KEY_SLIDER_TOP = 601;
    private static final int KEY_SLIDER_CENTER = 602;
    private static final int KEY_SLIDER_BOTTOM = 603;

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_V_UP_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE,
        KEY_SLIDER_TOP,
        KEY_SLIDER_CENTER,
        KEY_SLIDER_BOTTOM,
    };

    private static final int[] sHandledGestures = new int[]{
        KEY_SLIDER_TOP,
        KEY_SLIDER_CENTER,
        KEY_SLIDER_BOTTOM
    };

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final NotificationManager mNoMan;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;

    public KeyHandler(Context context) {
        mContext = context;
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mNoMan = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ProximityWakeLock");

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        try {
            mGestureContext = mContext.createPackageContext(
                    "com.slim.device", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            String action = null;
            switch(event.getScanCode()) {
            case GESTURE_CIRCLE_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_CIRCLE,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_SWIPE,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_V_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_UP,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_LTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
            }

            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }
    }
    private int getSliderAction(int position) {
        String value = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.BUTTON_EXTRA_KEY_MAPPING,
                    UserHandle.USER_CURRENT);
        final String defaultValue = SliderSettings.SLIDER_DEFAULT_VALUE;

        if (value == null) {
            value = defaultValue;
        } else if (value.indexOf(",") == -1) {
            value = defaultValue;
        }
        try {
            String[] parts = value.split(",");
            return Integer.valueOf(parts[position]);
        } catch (Exception e) {
        }
        return 0;
    }

    private void doHandleSliderAction(int position) {
        int action = getSliderAction(position);
        if ( action == 0) {
            mNoMan.setZenMode(Global.ZEN_MODE_OFF_ONLY, null, TAG);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        } else if (action == 1) {
            mNoMan.setZenMode(Global.ZEN_MODE_OFF_ONLY, null, TAG);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
        } else if (action == 2) {
            mNoMan.setZenMode(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, null, TAG);
        } else if (action == 3) {
            mNoMan.setZenMode(Global.ZEN_MODE_ALARMS, null, TAG);
        } else if (action == 4) {
            mNoMan.setZenMode(Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
            mVibrator.vibrate(50);
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                ScreenOffGesture.GESTURE_SETTINGS,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        boolean isKeySupported = ArrayUtils.contains(sHandledGestures, event.getScanCode());
        if (isKeySupported) {
            if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());
            switch(event.getScanCode()) {
                case KEY_SLIDER_TOP:
                    if (DEBUG) Log.i(TAG, "KEY_SLIDER_TOP");
                    doHandleSliderAction(0);
                    return true;
                case KEY_SLIDER_CENTER:
                    if (DEBUG) Log.i(TAG, "KEY_SLIDER_CENTER");
                    doHandleSliderAction(1);
                    return true;
                case KEY_SLIDER_BOTTOM:
                    if (DEBUG) Log.i(TAG, "KEY_SLIDER_BOTTOM");
                    doHandleSliderAction(2);
                    return true;
            }
        }

        return isKeySupported;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

}
