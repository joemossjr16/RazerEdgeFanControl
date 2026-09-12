package com.joemo.razeredgefan;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Wraps the sysfs node picked in {@link DiscoveryActivity} and turns a 0-100 slider value
 * into whatever discrete range that node actually uses (a thermal cooling_device's
 * 0..max_state, or a raw 0-255 PWM duty cycle).
 */
final class FanController {

    private static final String PREFS = "fan_control";
    private static final String KEY_PATH = "fan_path";
    private static final String KEY_MAX_STATE = "fan_max_state";
    private static final String KEY_THERMAL_SERVICE = "thermal_service";
    private static final String KEY_LAST_PERCENT = "last_percent";

    private static final String DEFAULT_THERMAL_SERVICE = "vendor.thermal-engine";

    private final SharedPreferences prefs;

    FanController(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isConfigured() {
        return getPath() != null;
    }

    String getPath() {
        return prefs.getString(KEY_PATH, null);
    }

    int getMaxState() {
        return prefs.getInt(KEY_MAX_STATE, 255);
    }

    String getThermalService() {
        return prefs.getString(KEY_THERMAL_SERVICE, DEFAULT_THERMAL_SERVICE);
    }

    int getLastPercent() {
        return prefs.getInt(KEY_LAST_PERCENT, 50);
    }

    void setThermalService(String serviceName) {
        prefs.edit().putString(KEY_THERMAL_SERVICE, serviceName).apply();
    }

    void configure(String path, int maxState) {
        prefs.edit()
                .putString(KEY_PATH, path)
                .putInt(KEY_MAX_STATE, Math.max(1, maxState))
                .apply();
    }

    /** Writes the raw value that corresponds to {@code percent} (0-100) straight to the node. */
    RootShell.Result applyPercent(int percent) {
        String path = getPath();
        if (path == null) {
            return new RootShell.Result(false, "No fan node configured yet - run Discover first.");
        }
        int clamped = Math.max(0, Math.min(100, percent));
        int value = Math.round((clamped / 100f) * getMaxState());
        prefs.edit().putInt(KEY_LAST_PERCENT, clamped).apply();
        return RootShell.run("echo " + value + " > " + path);
    }

    /**
     * Hands control back to the platform's own thermal governor by bouncing its service,
     * rather than guessing at a magic "auto" value for a node we don't control the meaning of.
     */
    RootShell.Result resetToAuto() {
        String service = getThermalService();
        return RootShell.run("stop " + service + "; start " + service);
    }
}
