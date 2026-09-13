package com.joemo.razeredgefan;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub Releases for a newer build than what's installed, and can hand a matching
 * release APK asset to the system DownloadManager + package installer. Only the `root` flavor
 * ships to testers, so this is the only build that needs to self-update.
 */
final class UpdateChecker {

    private static final String REPO = "joemossjr16/RazerEdgeFanControl";
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String APK_ASSET_NAME = "EdgePerformanceControl-root.apk";

    static final class UpdateInfo {
        final String version;
        final String downloadUrl;
        final String notes;

        UpdateInfo(String version, String downloadUrl, String notes) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.notes = notes;
        }
    }

    private UpdateChecker() {
    }

    /**
     * Off the main thread only. Returns null both when already up to date and when the check
     * itself fails (no network, GitHub unreachable, malformed response, matching asset missing)
     * - callers can't tell those apart, which is fine since both mean "nothing to offer right now".
     */
    static UpdateInfo checkForUpdate(String currentVersion) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            } finally {
                conn.disconnect();
            }

            JSONObject release = new JSONObject(body.toString());
            String tag = release.getString("tag_name").replaceFirst("^v", "");
            if (!isNewer(tag, currentVersion)) {
                return null;
            }

            JSONArray assets = release.getJSONArray("assets");
            String downloadUrl = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (APK_ASSET_NAME.equals(asset.getString("name"))) {
                    downloadUrl = asset.getString("browser_download_url");
                    break;
                }
            }
            if (downloadUrl == null) {
                return null;
            }
            return new UpdateInfo(tag, downloadUrl, release.optString("body", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Dotted-numeric version comparison, e.g. "0.1.10" > "0.1.9" (unlike a plain string compare). */
    private static boolean isNewer(String remote, String local) {
        String[] r = remote.split("\\.");
        String[] l = local.split("\\.");
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? parsePart(r[i]) : 0;
            int lv = i < l.length ? parsePart(l[i]) : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }
        return false;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Queues the APK download via the system DownloadManager; returns the download id. */
    static long startDownload(Context context, String url, String version) {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        String fileName = "EdgePerformanceControl-" + version + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Edge Performance Control v" + version)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive");
        return manager.enqueue(request);
    }
}
