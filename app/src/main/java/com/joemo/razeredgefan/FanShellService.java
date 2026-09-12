package com.joemo.razeredgefan;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Runs inside the Shizuku server process, not this app's process - Shizuku instantiates it
 * directly (reflection, no-arg constructor required) under whatever uid its server was started
 * as. On this device that's root, since the server was bootstrapped from a rooted `adb shell`,
 * so commands here run as root with no separate "su" call needed.
 */
public class FanShellService extends IFanShellService.Stub {

    public FanShellService() {
    }

    @Override
    public String run(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            StringBuilder output = new StringBuilder();
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = stdout.readLine()) != null) {
                output.append(line).append('\n');
            }
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = stderr.readLine()) != null) {
                output.append(line).append('\n');
            }
            int exitCode = process.waitFor();
            return exitCode + "|" + output.toString().trim();
        } catch (Exception e) {
            return "-1|" + e.getMessage();
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
