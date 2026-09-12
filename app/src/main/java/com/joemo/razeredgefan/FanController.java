package com.joemo.razeredgefan;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Drives the fan directly over i2c. Confirmed on-device (2026-09-12, root adb shell): the fan
 * is a MAX31760 at i2c bus 0, address 0x50, already running in Direct Fan Control mode
 * (CR2 bit0 = 1). Writing its PWMR register (0x50) with `i2cset -f` immediately and durably
 * changed real RPM (~14700 -> ~3900 at duty 0x40, back to ~17000 at 0xFF) - it does NOT get
 * reverted by the kernel's thermal governor the way the generic thermal cooling_device sysfs
 * node did (that path was tried first and does nothing; see project memory).
 *
 * `-f` (force) is required because the vendor kernel driver still holds the i2c device open.
 */
final class FanController {

    private static final String PREFS = "fan_control";
    private static final String KEY_BUS = "i2c_bus";
    private static final String KEY_ADDR = "i2c_addr";
    private static final String KEY_REG = "i2c_reg";
    private static final String KEY_THERMAL_SERVICE = "thermal_service";
    private static final String KEY_LAST_PERCENT = "last_percent";

    private static final int DEFAULT_BUS = 0;
    private static final String DEFAULT_ADDR = "0x50";
    private static final String DEFAULT_REG = "0x50";

    // Confirmed via `getprop init.svc.thermal-engine` / `ps -A` on-device - the running init
    // service is "thermal-engine", not "vendor.thermal-engine".
    private static final String DEFAULT_THERMAL_SERVICE = "thermal-engine";

    // A conservative fallback if the user never touched the slider - safety over silence.
    private static final int RESET_SAFE_DUTY = 220;

    static final String TELEMETRY_RPM_PATH = "/sys/class/thermal/cooling_device22/fan_speed";

    private final SharedPreferences prefs;

    FanController(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int getBus() {
        return prefs.getInt(KEY_BUS, DEFAULT_BUS);
    }

    String getAddr() {
        return prefs.getString(KEY_ADDR, DEFAULT_ADDR);
    }

    String getReg() {
        return prefs.getString(KEY_REG, DEFAULT_REG);
    }

    String getThermalService() {
        return prefs.getString(KEY_THERMAL_SERVICE, DEFAULT_THERMAL_SERVICE);
    }

    int getLastPercent() {
        return prefs.getInt(KEY_LAST_PERCENT, 50);
    }

    void configureTarget(int bus, String addr, String reg) {
        prefs.edit()
                .putInt(KEY_BUS, bus)
                .putString(KEY_ADDR, addr)
                .putString(KEY_REG, reg)
                .apply();
    }

    void setThermalService(String serviceName) {
        prefs.edit().putString(KEY_THERMAL_SERVICE, serviceName).apply();
    }

    private String targetDescription() {
        return "bus " + getBus() + ", addr " + getAddr() + ", reg " + getReg();
    }

    /** Maps 0-100% onto the PWMR register's 0-255 duty range and writes it. */
    RootShell.Result applyPercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int value = Math.round((clamped / 100f) * 255f);
        prefs.edit().putInt(KEY_LAST_PERCENT, clamped).apply();
        return writeRaw(value);
    }

    RootShell.Result writeRaw(int value0to255) {
        int clamped = Math.max(0, Math.min(255, value0to255));
        String hex = String.format("0x%02X", clamped);
        String cmd = "i2cset -fy " + getBus() + " " + getAddr() + " " + getReg() + " " + hex + " b";
        return PrivilegedShell.run(cmd);
    }

    /** Reads the PWMR register back (0-255) via i2cget, independent of the kernel driver's
     *  own (unreliable) cur_state/fan_duty reporting. */
    RootShell.Result readRawDuty() {
        String cmd = "i2cget -fy " + getBus() + " " + getAddr() + " " + getReg();
        return PrivilegedShell.run(cmd);
    }

    RootShell.Result readRpm() {
        return PrivilegedShell.run("cat " + TELEMETRY_RPM_PATH);
    }

    /**
     * There's no confirmed way (yet) to hand control back to a real hardware/software auto
     * curve - direct mode (DFC) was already enabled before this app touched anything, and what
     * (if anything) normally re-drives PWMR in response to temperature is still unknown. So
     * this restarts the thermal service on the chance it does reassert something, AND falls
     * back to a conservative fixed duty rather than silently doing nothing.
     */
    RootShell.Result resetToAuto() {
        String service = getThermalService();
        PrivilegedShell.run("stop " + service + "; start " + service);
        return writeRaw(RESET_SAFE_DUTY);
    }

    @Override
    public String toString() {
        return targetDescription();
    }
}
