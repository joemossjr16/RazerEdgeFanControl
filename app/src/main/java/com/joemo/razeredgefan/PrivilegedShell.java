package com.joemo.razeredgefan;

/**
 * Single entry point the rest of the app uses to run privileged commands, so callers don't
 * need to know whether Shizuku or a real `su` binary ended up providing the privilege.
 */
final class PrivilegedShell {

    private PrivilegedShell() {
    }

    enum Backend {
        NONE, SHIZUKU_NEEDS_PERMISSION, SHIZUKU, SU
    }

    /** Off the main thread only. */
    static Backend detect() {
        if (ShizukuBackend.isShizukuRunning()) {
            return ShizukuBackend.hasPermission() ? Backend.SHIZUKU : Backend.SHIZUKU_NEEDS_PERMISSION;
        }
        if (BuildConfig.ALLOW_SU_FALLBACK && RootShell.isRootAvailable()) {
            return Backend.SU;
        }
        return Backend.NONE;
    }

    /**
     * Off the main thread only. The Shizuku build never falls back to {@code su} - on a
     * genuinely non-root device there's no binary to find, and probing for one is pointless
     * work (and would misrepresent this as a rooted build if some other app's su shim answered).
     */
    static RootShell.Result run(String command) {
        if (ShizukuBackend.isShizukuRunning() && ShizukuBackend.hasPermission()) {
            return ShizukuBackend.run(command);
        }
        if (BuildConfig.ALLOW_SU_FALLBACK) {
            return RootShell.run(command);
        }
        return new RootShell.Result(false, "Shizuku unavailable (not running or permission not granted).");
    }
}
