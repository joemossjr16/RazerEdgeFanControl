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
        if (RootShell.isRootAvailable()) {
            return Backend.SU;
        }
        return Backend.NONE;
    }

    /** Off the main thread only. */
    static RootShell.Result run(String command) {
        if (ShizukuBackend.isShizukuRunning() && ShizukuBackend.hasPermission()) {
            return ShizukuBackend.run(command);
        }
        return RootShell.run(command);
    }
}
