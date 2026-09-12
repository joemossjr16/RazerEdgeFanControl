package com.joemo.razeredgefan;

import java.util.ArrayList;
import java.util.List;

/**
 * Nobody has published the exact sysfs node the Edge uses for its fan, so instead of
 * guessing a hardcoded path, this walks the two places a Qualcomm handheld normally
 * exposes one - the generic thermal "cooling device" framework, and any hwmon PWM/fan
 * inputs - and lets the user pick the right one from what's actually on their unit.
 */
final class SysfsScanner {

    static final class Node {
        final String kind;
        final String path;
        final String label;
        final String current;
        final int maxState;

        Node(String kind, String path, String label, String current, int maxState) {
            this.kind = kind;
            this.path = path;
            this.label = label;
            this.current = current;
            this.maxState = maxState;
        }

        @Override
        public String toString() {
            String state = maxState > 0 ? (current + " / " + maxState) : current;
            return kind + ": " + label + "\n" + path + "  [" + state + "]";
        }
    }

    private SysfsScanner() {
    }

    private static final String SCAN_SCRIPT =
            "for d in /sys/class/thermal/cooling_device*; do "
                    + "[ -d \"$d\" ] || continue; "
                    + "t=$(cat \"$d/type\" 2>/dev/null); "
                    + "c=$(cat \"$d/cur_state\" 2>/dev/null); "
                    + "m=$(cat \"$d/max_state\" 2>/dev/null); "
                    + "echo \"COOL|$d/cur_state|$t|$c|$m\"; "
                    + "done; "
                    + "for d in /sys/class/hwmon/hwmon*; do "
                    + "[ -d \"$d\" ] || continue; "
                    + "n=$(cat \"$d/name\" 2>/dev/null); "
                    + "for f in \"$d\"/fan*_input \"$d\"/pwm[0-9]; do "
                    + "[ -e \"$f\" ] || continue; "
                    + "v=$(cat \"$f\" 2>/dev/null); "
                    + "echo \"HWMON|$f|$n|$v|\"; "
                    + "done; "
                    + "done";

    static List<Node> scan() {
        List<Node> nodes = new ArrayList<>();
        RootShell.Result result = RootShell.run(SCAN_SCRIPT);
        for (String line : result.output.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) {
                continue;
            }
            String kind = parts[0];
            String path = parts[1];
            if (path.contains("*") || path.isEmpty()) {
                continue;
            }
            String label = parts[2].isEmpty() ? "(unnamed)" : parts[2];
            String current = parts[3];
            int maxState = 0;
            if (!parts[4].isEmpty()) {
                try {
                    maxState = Integer.parseInt(parts[4].trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (kind.equals("HWMON") && path.contains("pwm")) {
                maxState = 255;
            }
            nodes.add(new Node(kind, path, label, current, maxState));
        }
        return nodes;
    }
}
