package com.joemo.razeredgefan;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Loosens the skin-temperature comfort throttles (QUIET_THERM_MITIGATION, SS-SILVER, SS-APC1)
 * in thermal-engine.conf via a proper Magisk/APatch module overlay, NOT a live /vendor write.
 *
 * A live `mount -o remount,rw /vendor` + direct write was the original approach, but on ROMs
 * where /vendor is a dm-verity-protected partition with an overlayfs layered on top for
 * writable-system support (lowerdir=/vendor, upperdir=/mnt/scratch/overlay/vendor/upper - the
 * modern Android mechanism, seen on a LineageOS build for this device), that remount bypasses
 * the overlay and writes directly to its lowerdir while it's mounted - which the kernel's own
 * overlayfs documentation calls out as unsafe. Confirmed on real hardware: it reliably crashed
 * the device (isolated down to the plain `cp` alone, no service restart needed to reproduce).
 *
 * A module avoids this entirely: Magisk/APatch's own mount daemon overlays module files onto
 * the real path using its own controlled mechanism, which already coexists correctly with
 * whatever overlay/verity scheme the ROM uses (it's the same technique used by every real
 * Magisk/APatch module targeting /system or /vendor). The tradeoff is that module mounts only
 * happen at boot - switching profiles now requires a reboot, which is a fully acceptable
 * tradeoff for not crashing the device.
 *
 * Real hardware protection - the 94C NSP trip and the 77-79C core hotplug monitor in
 * CPUSS-NSP-MONITOR - is identical to stock and untouched by this module.
 */
final class ThermalProfileController {

    enum Profile { STOCK, PERFORMANCE, UNKNOWN }

    private static final String MODULE_ID = "edgefan_thermal_performance";
    private static final String MODULE_DIR = "/data/adb/modules/" + MODULE_ID;
    private static final String DISABLE_MARKER = MODULE_DIR + "/disable";

    private static final String MODULE_PROP =
            "id=" + MODULE_ID + "\n"
                    + "name=Edge Fan Control - Performance Thermal Profile\n"
                    + "version=v1\n"
                    + "versionCode=1\n"
                    + "author=joemo\n"
                    + "description=Loosens the skin-temp comfort throttle curve via a module file "
                    + "overlay (see Edge Fan Control app). Disable this module to restore the ROM's "
                    + "own stock thermal-engine.conf. A reboot is required after enabling/disabling.\n";

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

    /**
     * Off the main thread only. Never touches /vendor directly - installs or disables a proper
     * module instead. Takes effect on next reboot only; callers should prompt for one.
     */
    RootShell.Result apply(Profile profile) {
        if (profile == Profile.PERFORMANCE) {
            return installModule();
        }
        return disableModule();
    }

    private RootShell.Result installModule() {
        File propFile = new File(context.getFilesDir(), "module.prop");
        File confFile = new File(context.getFilesDir(), "thermal-engine.conf");
        try (FileWriter writer = new FileWriter(propFile)) {
            writer.write(MODULE_PROP);
        } catch (IOException e) {
            return new RootShell.Result(false, "Failed writing module.prop: " + e.getMessage());
        }
        try (FileWriter writer = new FileWriter(confFile)) {
            writer.write(PERFORMANCE_CONF);
        } catch (IOException e) {
            return new RootShell.Result(false, "Failed writing thermal-engine.conf: " + e.getMessage());
        }
        String cmd = "mkdir -p " + MODULE_DIR + "/vendor/etc"
                + " && cp '" + propFile.getAbsolutePath() + "' " + MODULE_DIR + "/module.prop"
                + " && cp '" + confFile.getAbsolutePath() + "' " + MODULE_DIR + "/vendor/etc/thermal-engine.conf"
                + " && chmod 644 " + MODULE_DIR + "/module.prop " + MODULE_DIR + "/vendor/etc/thermal-engine.conf"
                + " && rm -f " + DISABLE_MARKER + " " + MODULE_DIR + "/remove";
        return PrivilegedShell.run(cmd);
    }

    private RootShell.Result disableModule() {
        return PrivilegedShell.run("mkdir -p " + MODULE_DIR + " && touch " + DISABLE_MARKER);
    }

    /**
     * Off the main thread only. Reflects what's *configured* to apply on next boot, not
     * necessarily what's active right now this session (module mounts only happen at boot).
     */
    Profile readCurrent() {
        RootShell.Result result = PrivilegedShell.run(
                "[ -d " + MODULE_DIR + " ] && [ ! -f " + DISABLE_MARKER + " ] && echo ENABLED || echo DISABLED");
        if (!result.success) {
            return Profile.UNKNOWN;
        }
        return result.output.trim().endsWith("ENABLED") ? Profile.PERFORMANCE : Profile.STOCK;
    }
}
