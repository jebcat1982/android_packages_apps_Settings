/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.settings.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.IHardwareService;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;
import com.android.settings.R;
import com.android.settings.bluetooth.LocalBluetoothManager;

/**
 * Provides control of power-related settings from a widget.
 */
public class SettingsAppWidgetProvider extends AppWidgetProvider {
    static final String TAG = "SettingsAppWidgetProvider";

    static final ComponentName THIS_APPWIDGET =
            new ComponentName("com.android.settings",
                    "com.android.settings.widget.SettingsAppWidgetProvider");

    private static LocalBluetoothManager mLocalBluetoothManager = null;

    private static final int BUTTON_WIFI = 0;
    private static final int BUTTON_BRIGHTNESS = 1;
    private static final int BUTTON_SYNC = 2;
    private static final int BUTTON_GPS = 3;
    private static final int BUTTON_BLUETOOTH = 4;

    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED = 1;
    private static final int STATE_INTERMEDIATE = 2;

    /**
     * Minimum and maximum brightnesses.  Don't go to 0 since that makes the display unusable
     */
    private static final int MINIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_DIM + 10;
    private static final int MAXIMUM_BACKLIGHT = android.os.Power.BRIGHTNESS_ON;
    private static final int DEFAULT_BACKLIGHT = (int) (android.os.Power.BRIGHTNESS_ON * 0.4f);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        // Update each requested appWidgetId
        RemoteViews view = buildUpdate(context, -1);

        for (int i = 0; i < appWidgetIds.length; i++) {
            appWidgetManager.updateAppWidget(appWidgetIds[i], view);
        }
    }

    @Override
    public void onEnabled(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName("com.android.settings", ".widget.SettingsAppWidgetProvider"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onDisabled(Context context) {
        Class clazz = com.android.settings.widget.SettingsAppWidgetProvider.class;
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName("com.android.settings", ".widget.SettingsAppWidgetProvider"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * Load image for given widget and build {@link RemoteViews} for it.
     */
    static RemoteViews buildUpdate(Context context, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget);
        views.setOnClickPendingIntent(R.id.btn_wifi, getLaunchPendingIntent(context, appWidgetId,
                BUTTON_WIFI));
        views.setOnClickPendingIntent(R.id.btn_brightness,
                getLaunchPendingIntent(context,
                        appWidgetId, BUTTON_BRIGHTNESS));
        views.setOnClickPendingIntent(R.id.btn_sync,
                getLaunchPendingIntent(context,
                        appWidgetId, BUTTON_SYNC));
        views.setOnClickPendingIntent(R.id.btn_gps,
                getLaunchPendingIntent(context, appWidgetId, BUTTON_GPS));
        views.setOnClickPendingIntent(R.id.btn_bluetooth,
                getLaunchPendingIntent(context,
                        appWidgetId, BUTTON_BLUETOOTH));

        updateButtons(views, context);
        return views;
    }

    /**
     * Updates the widget when something changes, or when a button is pushed.
     *
     * @param context
     */
    public static void updateWidget(Context context) {
        RemoteViews views = buildUpdate(context, -1);
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        gm.updateAppWidget(THIS_APPWIDGET, views);
    }

    /**
     * Updates the buttons based on the underlying states of wifi, etc.
     *
     * @param views   The RemoteViews to update.
     * @param context
     */
    private static void updateButtons(RemoteViews views, Context context) {
        switch (getWifiState(context)) {
            case STATE_DISABLED:
                views.setImageViewResource(R.id.img_wifi, R.drawable.ic_appwidget_settings_wifi_off);
                views.setImageViewResource(R.id.ind_wifi, R.drawable.appwidget_settings_ind_off_l);
                break;
            case STATE_ENABLED:
                views.setImageViewResource(R.id.img_wifi, R.drawable.ic_appwidget_settings_wifi_on);
                views.setImageViewResource(R.id.ind_wifi, R.drawable.appwidget_settings_ind_on_l);
                break;
            case STATE_INTERMEDIATE:
                views.setImageViewResource(R.id.img_wifi, R.drawable.ic_appwidget_settings_wifi_off);
                views.setImageViewResource(R.id.ind_wifi, R.drawable.appwidget_settings_ind_mid_l);
                break;
        }
        if (getBrightness(context)) {
            views.setImageViewResource(R.id.img_brightness, R.drawable.ic_appwidget_settings_brightness_on);
            views.setImageViewResource(R.id.ind_brightness, R.drawable.appwidget_settings_ind_on_r);
        } else {
            views.setImageViewResource(R.id.img_brightness, R.drawable.ic_appwidget_settings_brightness_off);
            views.setImageViewResource(R.id.ind_brightness, R.drawable.appwidget_settings_ind_off_r);
        }
        if (getBackgroundDataState(context)) {
            views.setImageViewResource(R.id.img_sync, R.drawable.ic_appwidget_settings_sync_on);
            views.setImageViewResource(R.id.ind_sync, R.drawable.appwidget_settings_ind_on_c);
        } else {
            views.setImageViewResource(R.id.img_sync, R.drawable.ic_appwidget_settings_sync_off);
            views.setImageViewResource(R.id.ind_sync, R.drawable.appwidget_settings_ind_off_c);
        }
        if (getGpsState(context)) {
            views.setImageViewResource(R.id.img_gps, R.drawable.ic_appwidget_settings_gps_on);
            views.setImageViewResource(R.id.ind_gps, R.drawable.appwidget_settings_ind_on_c);
        } else {
            views.setImageViewResource(R.id.img_gps, R.drawable.ic_appwidget_settings_gps_off);
            views.setImageViewResource(R.id.ind_gps, R.drawable.appwidget_settings_ind_off_c);
        }
        switch (getBluetoothState(context)) {
            case STATE_DISABLED:
                views.setImageViewResource(R.id.img_bluetooth, R.drawable.ic_appwidget_settings_bluetooth_off);
                views.setImageViewResource(R.id.ind_bluetooth, R.drawable.appwidget_settings_ind_off_c);
                break;
            case STATE_ENABLED:
                views.setImageViewResource(R.id.img_bluetooth, R.drawable.ic_appwidget_settings_bluetooth_on);
                views.setImageViewResource(R.id.ind_bluetooth, R.drawable.appwidget_settings_ind_on_c);
                break;
            case STATE_INTERMEDIATE:
                views.setImageViewResource(R.id.img_bluetooth, R.drawable.ic_appwidget_settings_bluetooth_off);
                views.setImageViewResource(R.id.ind_bluetooth, R.drawable.appwidget_settings_ind_mid_c);
                break;
        }
    }

    /**
     * Creates PendingIntent to notify the widget of a button click.
     *
     * @param context
     * @param appWidgetId
     * @return
     */
    private static PendingIntent getLaunchPendingIntent(Context context, int appWidgetId, int buttonId) {
        Intent launchIntent = new Intent();
        launchIntent.setClass(context, SettingsAppWidgetProvider.class);
        launchIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        launchIntent.setData(Uri.parse("custom:" + buttonId));
        PendingIntent pi = PendingIntent.getBroadcast(context, 0 /* no requestCode */,
                launchIntent, 0 /* no flags */);
        return pi;
    }

    /**
     * Receives and processes a button pressed intent or state change.
     *
     * @param context
     * @param intent  Indicates the pressed button.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.hasCategory(Intent.CATEGORY_ALTERNATIVE)) {
            Uri data = intent.getData();
            int buttonId = Integer.parseInt(data.getSchemeSpecificPart());
            if (buttonId == BUTTON_WIFI) {
                toggleWifi(context);
            } else if (buttonId == BUTTON_BRIGHTNESS) {
                toggleBrightness(context);
            } else if (buttonId == BUTTON_SYNC) {
                toggleBackgroundData(context);
            } else if (buttonId == BUTTON_GPS) {
                toggleGps(context);
            } else if (buttonId == BUTTON_BLUETOOTH) {
                toggleBluetooth(context);
            }
        }
        // State changes fall through
        updateWidget(context);
    }

    /**
     * Gets the state of Wi-Fi
     *
     * @param context
     * @return STATE_ENABLED, STATE_DISABLED, or STATE_INTERMEDIATE
     */
    private static int getWifiState(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int wifiState = wifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
            return STATE_DISABLED;
        } else if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            return STATE_ENABLED;
        } else {
            return STATE_INTERMEDIATE;
        }
    }

    /**
     * Toggles the state of Wi-Fi
     *
     * @param context
     */
    private void toggleWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int wifiState = getWifiState(context);
        if (wifiState == STATE_ENABLED) {
            wifiManager.setWifiEnabled(false);
        } else if (wifiState == STATE_DISABLED) {
            wifiManager.setWifiEnabled(true);
        }
    }

    /**
     * Gets the state of background data.
     *
     * @param context
     * @return true if enabled
     */
    private static boolean getBackgroundDataState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connManager.getBackgroundDataSetting();
    }

    /**
     * Toggle background data and sync tickles.
     *
     * @param context
     */
    private void toggleBackgroundData(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean sync = getBackgroundDataState(context);
        connManager.setBackgroundDataSetting(!sync);

        IContentService contentService = ContentResolver.getContentService();
        try {
            contentService.setListenForNetworkTickles(!sync);
        } catch (RemoteException e) {
            Log.d(TAG, "toggleBackgroundData: " + e);
        }
    }

    /**
     * Gets the state of GPS location.
     *
     * @param context
     * @return true if enabled.
     */
    private static boolean getGpsState(Context context) {
        ContentResolver resolver = context.getContentResolver();
        return Settings.Secure.isLocationProviderEnabled(resolver, LocationManager.GPS_PROVIDER);
    }

    /**
     * Toggles the state of GPS.
     *
     * @param context
     */
    private void toggleGps(Context context) {
        ContentResolver resolver = context.getContentResolver();
        boolean enabled = getGpsState(context);
        Settings.Secure.setLocationProviderEnabled(resolver, LocationManager.GPS_PROVIDER, !enabled);
    }

    /**
     * Gets state of brightness.
     *
     * @param context
     * @return true if more than moderately bright.
     */
    private static boolean getBrightness(Context context) {
        try {
            IHardwareService hardware = IHardwareService.Stub.asInterface(
                    ServiceManager.getService("hardware"));
            if (hardware != null) {
                int brightness = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS);
                return brightness > 100;
            }
        } catch (Exception e) {
            Log.d(TAG, "getBrightness: " + e);
        }
        return false;
    }

    /**
     * Increases or decreases the brightness.
     *
     * @param context
     */
    private void toggleBrightness(Context context) {
        try {
            IPowerManager pm = IPowerManager.Stub.asInterface(
            		ServiceManager.getService(Context.POWER_SERVICE));
            
            if (pm != null) {
                ContentResolver cr = context.getContentResolver();
                int brightness = Settings.System.getInt(cr,
                        Settings.System.SCREEN_BRIGHTNESS);
                // Rotate MINIMUM -> DEFAULT -> MAXIMUM
                // Technically, not a toggle...
                if (brightness < DEFAULT_BACKLIGHT) {
                    brightness = DEFAULT_BACKLIGHT;
                } else if (brightness < MAXIMUM_BACKLIGHT) {
                    brightness = MAXIMUM_BACKLIGHT;
                } else {
                    brightness = MINIMUM_BACKLIGHT;
                }
                pm.setScreenBrightnessOverride(brightness);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightness);
                brightness = Settings.System.getInt(cr,
                        Settings.System.SCREEN_BRIGHTNESS);
            }
        } catch (RemoteException e) {
            Log.d(TAG, "toggleBrightness: " + e);
        } catch (Settings.SettingNotFoundException e) {
            Log.d(TAG, "toggleBrightness: " + e);
        }
    }

    /**
     * Gets state of bluetooth
     *
     * @param context
     * @return STATE_ENABLED, STATE_DISABLED, or STATE_INTERMEDIATE
     */
    private static int getBluetoothState(Context context) {
        if (mLocalBluetoothManager == null) {
            mLocalBluetoothManager = LocalBluetoothManager.getInstance(context);
            if (mLocalBluetoothManager == null) {
                return STATE_INTERMEDIATE; // On emulator?
            }
        }
        int state = mLocalBluetoothManager.getBluetoothState();
        if (state == BluetoothDevice.BLUETOOTH_STATE_OFF) {
            return STATE_DISABLED;
        } else if (state == BluetoothDevice.BLUETOOTH_STATE_ON) {
            return STATE_ENABLED;
        } else {
            return STATE_INTERMEDIATE;
        }
    }

    /**
     * Toggles the state of bluetooth
     *
     * @param context
     */
    private void toggleBluetooth(Context context) {
        int state = getBluetoothState(context);
        if (state == STATE_ENABLED) {
            mLocalBluetoothManager.setBluetoothEnabled(false);
        } else if (state == STATE_DISABLED) {
            mLocalBluetoothManager.setBluetoothEnabled(true);
        }
    }
}
