package com.joemo.razeredgefan;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Talks to Shizuku instead of `su`, since this device has no system-wide su binary. Shizuku's
 * own server was bootstrapped once from a rooted `adb shell` (see README/memory notes), so it
 * runs as root - {@link FanShellService} inherits that.
 */
final class ShizukuBackend {

    static final int PERMISSION_REQUEST_CODE = 9100;

    private static final Shizuku.UserServiceArgs SERVICE_ARGS =
            new Shizuku.UserServiceArgs(new ComponentName("com.joemo.razeredgefan", FanShellService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("fanshell")
                    .debuggable(false)
                    .version(1);

    private static volatile IFanShellService service;
    private static volatile CountDownLatch bindLatch;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IFanShellService.Stub.asInterface(binder);
            CountDownLatch latch = bindLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    private ShizukuBackend() {
    }

    static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hasPermission() {
        try {
            return isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static void requestPermission() {
        if (isShizukuRunning()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        }
    }

    private static synchronized boolean ensureBound() {
        if (service != null) {
            return true;
        }
        if (!hasPermission()) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        bindLatch = latch;
        Shizuku.bindUserService(SERVICE_ARGS, CONNECTION);
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return service != null;
    }

    /** Must be called off the main thread - binding blocks briefly on first use. */
    static RootShell.Result run(String command) {
        if (!ensureBound()) {
            return new RootShell.Result(false, "Shizuku service unavailable (not running or permission not granted).");
        }
        try {
            String raw = service.run(command);
            int sep = raw.indexOf('|');
            int exitCode = Integer.parseInt(raw.substring(0, sep));
            String output = raw.substring(sep + 1);
            return new RootShell.Result(exitCode == 0, output);
        } catch (Exception e) {
            service = null;
            return new RootShell.Result(false, "Shizuku call failed: " + e.getMessage());
        }
    }
}
