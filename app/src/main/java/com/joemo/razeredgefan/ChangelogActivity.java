package com.joemo.razeredgefan;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Fetches CHANGELOG.md straight from the GitHub repo rather than bundling a copy in the app -
 * one source of truth, and it never goes stale between releases.
 */
public class ChangelogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changelog);

        TextView body = findViewById(R.id.changelog_body);
        new Thread(() -> {
            String changelog = UpdateChecker.fetchChangelog();
            runOnUiThread(() -> body.setText(changelog != null
                    ? changelog
                    : "Couldn't load the changelog - check your internet connection and try again."));
        }).start();
    }
}
