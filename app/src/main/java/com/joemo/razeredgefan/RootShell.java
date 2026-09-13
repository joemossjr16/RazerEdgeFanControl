package com.joemo.razeredgefan;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * Runs shell commands through {@code su}. The fan control nodes on the Edge live under
 * /sys and are only writable by root, so every read/write in this app goes through here.
 *
 * Invoking the su binary is itself the request - Magisk/APatch/KernelSU show their own grant
 * prompt the first time a given app calls it and block until the user responds, which is why
 * {@link MainActivity} exposes an explicit "Grant Root Permission" button that just calls
 * {@link #run} again, rather than only checking passively on launch.
 */
final class RootShell {

    static final class Result {
        final boolean success;
        final String output;

        Result(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    private RootShell() {
    }

    static Result run(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
            stdin.writeBytes(command + "\n");
            stdin.writeBytes("exit\n");
            stdin.flush();
            stdin.close();

            StringBuilder combined = new StringBuilder();
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = stdout.readLine()) != null) {
                combined.append(line).append('\n');
            }
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = stderr.readLine()) != null) {
                combined.append(line).append('\n');
            }

            int exitCode = process.waitFor();
            return new Result(exitCode == 0, combined.toString().trim());
        } catch (Exception e) {
            return new Result(false, "su unavailable: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    static boolean isRootAvailable() {
        Result result = run("id");
        return result.success && result.output.contains("uid=0");
    }
}
