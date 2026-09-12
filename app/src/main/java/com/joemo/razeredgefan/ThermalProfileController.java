package com.joemo.razeredgefan;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Swaps /vendor/etc/thermal-engine.conf between the stock config and a "performance" variant
 * that only loosens the skin-temperature comfort throttles (QUIET_THERM_MITIGATION, SS-SILVER,
 * SS-APC1). Real hardware protection - the 94C NSP trip and the 77-79C core hotplug monitor in
 * CPUSS-NSP-MONITOR - is identical in both profiles.
 *
 * Both full configs are embedded here (rather than relying on the on-device
 * thermal-engine.conf.stock-backup this also creates) so "Stock" always restores exactly the
 * known-good original text regardless of what's on the device.
 */
final class ThermalProfileController {

    enum Profile { STOCK, PERFORMANCE, UNKNOWN }

    private static final String LIVE_PATH = "/vendor/etc/thermal-engine.conf";
    private static final String BACKUP_PATH = "/vendor/etc/thermal-engine.conf.stock-backup";
    private static final String THERMAL_SERVICE = "thermal-engine";

    private static final String STOCK_MARKER = "set_point 75000";
    private static final String PERFORMANCE_MARKER = "set_point 85000";

    private static final String STOCK_CONF =
            "# File empty by default.\n"
                    + "# Replace contents of this file with custom configuration.\n"
                    + "\n"
                    + "[VIRTUAL-CPU-NSP]\n"
                    + "#algo_type virtual\n"
                    + "sensors cpuss-0-usr cpuss-1-usr\n"
                    + "weights\n"
                    + "trip_sensors nspss-0-usr nspss-1-usr nspss-2-usr\n"
                    + "trip_sensors_logic\n"
                    + "thresholds 94000 94000 94000\n"
                    + "thresholds_clr 90000 90000 90000\n"
                    + "sampling 1000\n"
                    + "math 2\n"
                    + "\n"
                    + "[SS-SILVER]\n"
                    + "#algo_type ss\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "device cpu0\n"
                    + "set_point 75000\n"
                    + "set_point_clr 70000\n"
                    + "time_constant 0\n"
                    + "device_max_limit 806400\n"
                    + "\n"
                    + "[SS-APC1]\n"
                    + "#algo_type ss\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "device thermal-cluster-7-4\n"
                    + "set_point 75000\n"
                    + "set_point_clr 70000\n"
                    + "time_constant 0\n"
                    + "\n"
                    + "[CHARGE-MITIGATION]\n"
                    + "algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 38000 40000 42000 44000 46000\n"
                    + "thresholds_clr 36000 38000 40000 42000 44000\n"
                    + "actions battery battery battery battery battery\n"
                    + "action_info 1 2 3 4 5\n"
                    + "\n"
                    + "[FAN-MITIGATION]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 32000 34000 36000 38000 40000 42000\n"
                    + "thresholds_clr 30000 32000 34000 36000 38000 40000\n"
                    + "actions fan-max31760 fan-max31760 fan-max31760 fan-max31760 fan-max31760 fan-max31760\n"
                    + "action_info 2 3 4 5 6 7\n"
                    + "\n"
                    + "[QUIET_THERM_MITIGATION]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 40000 42000 44000 46000 51000\n"
                    + "thresholds_clr 38000 40000 42000 44000 48000\n"
                    + "actions cpu0+cpu4+cpu7 cpu0+cpu4+cpu7 cpu0+cpu4+cpu7 cpu0+cpu4+cpu7+gpu  cpu0+cpu4+gpu\n"
                    + "action_info 1804800+2419200+2688000  1804800+2112000+2265600 1612800+1881600+2035200 1612800+1881600+2035200+778000000 1612800+1766400+608000000\n"
                    + "\n"
                    + "\n"
                    + "\n"
                    + "[CPUSS-NSP-MONITOR]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "thresholds 77000 79000\n"
                    + "thresholds_clr 72000 74000\n"
                    + "actions hotplug_7 hotplug_4+hotplug_5+hotplug_6\n"
                    + "action_info 1 1+1+1\n";

    private static final String PERFORMANCE_CONF =
            "# Performance profile - loosens the \"comfortable to hold\" throttles only.\n"
                    + "# Real hardware protection (VIRTUAL-CPU-NSP 94C trip, CPUSS-NSP-MONITOR\n"
                    + "# 77-79C core hotplug, CHARGE-MITIGATION) is unchanged below.\n"
                    + "\n"
                    + "[VIRTUAL-CPU-NSP]\n"
                    + "#algo_type virtual\n"
                    + "sensors cpuss-0-usr cpuss-1-usr\n"
                    + "weights\n"
                    + "trip_sensors nspss-0-usr nspss-1-usr nspss-2-usr\n"
                    + "trip_sensors_logic\n"
                    + "thresholds 94000 94000 94000\n"
                    + "thresholds_clr 90000 90000 90000\n"
                    + "sampling 1000\n"
                    + "math 2\n"
                    + "\n"
                    + "[SS-SILVER]\n"
                    + "#algo_type ss\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "device cpu0\n"
                    + "set_point 85000\n"
                    + "set_point_clr 80000\n"
                    + "time_constant 0\n"
                    + "device_max_limit 1804800\n"
                    + "\n"
                    + "[SS-APC1]\n"
                    + "#algo_type ss\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "device thermal-cluster-7-4\n"
                    + "set_point 85000\n"
                    + "set_point_clr 80000\n"
                    + "time_constant 0\n"
                    + "\n"
                    + "[CHARGE-MITIGATION]\n"
                    + "algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 38000 40000 42000 44000 46000\n"
                    + "thresholds_clr 36000 38000 40000 42000 44000\n"
                    + "actions battery battery battery battery battery\n"
                    + "action_info 1 2 3 4 5\n"
                    + "\n"
                    + "[FAN-MITIGATION]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 32000 34000 36000 38000 40000 42000\n"
                    + "thresholds_clr 30000 32000 34000 36000 38000 40000\n"
                    + "actions fan-max31760 fan-max31760 fan-max31760 fan-max31760 fan-max31760 fan-max31760\n"
                    + "action_info 2 3 4 5 6 7\n"
                    + "\n"
                    + "[QUIET_THERM_MITIGATION]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor quiet-therm-usr\n"
                    + "thresholds 55000 57000 59000 61000 66000\n"
                    + "thresholds_clr 53000 55000 57000 59000 63000\n"
                    + "actions cpu0+cpu4+cpu7 cpu0+cpu4+cpu7 cpu0+cpu4+cpu7 cpu0+cpu4+cpu7+gpu  cpu0+cpu4+gpu\n"
                    + "action_info 1804800+2419200+2688000  1804800+2112000+2265600 1612800+1881600+2035200 1612800+1881600+2035200+778000000 1612800+1766400+608000000\n"
                    + "\n"
                    + "\n"
                    + "\n"
                    + "[CPUSS-NSP-MONITOR]\n"
                    + "#algo_type monitor\n"
                    + "sampling 1000\n"
                    + "sensor VIRTUAL-CPU-NSP\n"
                    + "thresholds 77000 79000\n"
                    + "thresholds_clr 72000 74000\n"
                    + "actions hotplug_7 hotplug_4+hotplug_5+hotplug_6\n"
                    + "action_info 1 1+1+1\n";

    private final Context context;

    ThermalProfileController(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Off the main thread only. */
    RootShell.Result apply(Profile profile) {
        String content = profile == Profile.PERFORMANCE ? PERFORMANCE_CONF : STOCK_CONF;
        File tmp = new File(context.getFilesDir(), "thermal-engine.conf.apply");
        try (FileWriter writer = new FileWriter(tmp)) {
            writer.write(content);
        } catch (IOException e) {
            return new RootShell.Result(false, "Failed writing profile: " + e.getMessage());
        }
        String cmd = "mount -o remount,rw /vendor"
                + " && ( [ -f " + BACKUP_PATH + " ] || cp " + LIVE_PATH + " " + BACKUP_PATH + " )"
                + " && cp '" + tmp.getAbsolutePath() + "' " + LIVE_PATH
                + " && chmod 644 " + LIVE_PATH
                + " && stop " + THERMAL_SERVICE + " && start " + THERMAL_SERVICE;
        return PrivilegedShell.run(cmd);
    }

    /** Off the main thread only. */
    Profile readCurrent() {
        RootShell.Result result = PrivilegedShell.run("cat " + LIVE_PATH);
        if (!result.success) {
            return Profile.UNKNOWN;
        }
        if (result.output.contains(PERFORMANCE_MARKER)) {
            return Profile.PERFORMANCE;
        }
        if (result.output.contains(STOCK_MARKER)) {
            return Profile.STOCK;
        }
        return Profile.UNKNOWN;
    }
}
