package com.joemo.razeredgefan;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private TextView rootStatus;
    private TextView pathStatus;
    private TextView percentLabel;
    private TextView logView;
    private SeekBar percentSeekBar;
    private Button grantRootButton;
    private Button grantShizukuButton;
    private Button rebootButton;
    private TextView thermalProfileStatus;
    private TextView updateStatus;
    private Button checkUpdateButton;
    private Button downloadUpdateButton;
    private FanController fanController;
    private ThermalProfileController thermalProfileController;
    private UpdateChecker.UpdateInfo pendingUpdate;
    private long pendingDownloadId = -1;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == -1 || id != pendingDownloadId) {
                return;
            }
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) {
                appendLog("Update download failed - check your connection and try again.");
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> runOnUiThread(this::checkRoot);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UiAnim.enter(findViewById(R.id.main_root));
        UiAnim.breathe(findViewById(R.id.title_text));

        fanController = new FanController(this);
        thermalProfileController = new ThermalProfileController(this);

        rootStatus = findViewById(R.id.root_status);
        pathStatus = findViewById(R.id.path_status);
        percentLabel = findViewById(R.id.percent_label);
        logView = findViewById(R.id.log_view);
        percentSeekBar = findViewById(R.id.percent_seekbar);
        grantRootButton = findViewById(R.id.grant_root_button);
        grantShizukuButton = findViewById(R.id.grant_shizuku_button);
        UiAnim.punch(grantRootButton);
        UiAnim.punch(grantShizukuButton);

        if (!BuildConfig.SUPPORTS_THERMAL_MODULE) {
            findViewById(R.id.thermal_profile_section).setVisibility(android.view.View.GONE);
            findViewById(R.id.thermal_profile_unavailable).setVisibility(android.view.View.VISIBLE);
        }
        thermalProfileStatus = findViewById(R.id.thermal_profile_status);
        rebootButton = findViewById(R.id.reboot_button);
        updateStatus = findViewById(R.id.update_status);
        checkUpdateButton = findViewById(R.id.check_update_button);
        downloadUpdateButton = findViewById(R.id.download_update_button);
        UiAnim.punch(checkUpdateButton);
        UiAnim.punch(downloadUpdateButton);
        UiAnim.punch(rebootButton);

        pathStatus.setText("Target: " + fanController);

        percentSeekBar.setProgress(fanController.getLastPercent());
        percentLabel.setText(fanController.getLastPercent() + "%");

        percentSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                percentLabel.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        for (int id : new int[] {R.id.discover_button, R.id.apply_button, R.id.preset_quiet,
                R.id.preset_balanced, R.id.preset_performance, R.id.preset_max, R.id.reset_auto_button,
                R.id.thermal_stock_button, R.id.thermal_performance_button, R.id.source_link,
                R.id.changelog_link}) {
            UiAnim.punch(findViewById(id));
        }

        findViewById(R.id.discover_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DiscoveryActivity.class)));

        findViewById(R.id.apply_button).setOnClickListener(v ->
                applyPercent(percentSeekBar.getProgress()));

        ((Button) findViewById(R.id.preset_quiet)).setOnClickListener(v -> applyPercent(20));
        ((Button) findViewById(R.id.preset_balanced)).setOnClickListener(v -> applyPercent(50));
        ((Button) findViewById(R.id.preset_performance)).setOnClickListener(v -> applyPercent(80));
        ((Button) findViewById(R.id.preset_max)).setOnClickListener(v -> applyPercent(100));

        findViewById(R.id.reset_auto_button).setOnClickListener(v -> resetToAuto());

        findViewById(R.id.thermal_stock_button).setOnClickListener(v ->
                applyThermalProfile(ThermalProfileController.Profile.STOCK));
        findViewById(R.id.thermal_performance_button).setOnClickListener(v ->
                applyThermalProfile(ThermalProfileController.Profile.PERFORMANCE));
        rebootButton.setOnClickListener(v -> new Thread(() -> PrivilegedShell.run("reboot")).start());

        grantShizukuButton.setOnClickListener(v -> ShizukuBackend.requestPermission());
        grantRootButton.setOnClickListener(v -> requestRoot());
        checkUpdateButton.setOnClickListener(v -> checkForUpdate(true));
        downloadUpdateButton.setOnClickListener(v -> downloadAndInstallUpdate());
        findViewById(R.id.source_link).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL))));
        findViewById(R.id.changelog_link).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ChangelogActivity.class)));

        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, downloadFilter);
        }

        Shizuku.addRequestPermissionResultListener(permissionListener);
        checkRoot();
        if (BuildConfig.SUPPORTS_THERMAL_MODULE) {
            checkThermalProfile();
        }
        checkForUpdate(false);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        unregisterReceiver(downloadReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pathStatus.setText("Target: " + fanController);
    }

    private void checkRoot() {
        rootStatus.setText("Checking privileged access...");
        new Thread(() -> {
            final PrivilegedShell.Backend backend = PrivilegedShell.detect();
            runOnUiThread(() -> {
                grantRootButton.setVisibility(android.view.View.GONE);
                switch (backend) {
                    case SHIZUKU:
                        rootStatus.setText("Privileged access: Shizuku (granted)");
                        grantShizukuButton.setVisibility(android.view.View.GONE);
                        break;
                    case SHIZUKU_NEEDS_PERMISSION:
                        rootStatus.setText("Shizuku is running but needs permission");
                        grantShizukuButton.setVisibility(android.view.View.VISIBLE);
                        break;
                    case SU:
                        rootStatus.setText("Privileged access: su (granted)");
                        grantShizukuButton.setVisibility(android.view.View.GONE);
                        break;
                    default:
                        rootStatus.setText(BuildConfig.ALLOW_SU_FALLBACK
                                ? "No privileged access - tap below to request root."
                                : "No privileged access - start Shizuku and pair/grant it.");
                        grantShizukuButton.setVisibility(android.view.View.GONE);
                        grantRootButton.setVisibility(BuildConfig.ALLOW_SU_FALLBACK
                                ? android.view.View.VISIBLE : android.view.View.GONE);
                }
            });
        }).start();
    }

    /**
     * Calling `su` is itself the request - Magisk/APatch/KernelSU show their own grant prompt
     * the first time this app calls it and block until the user responds. This just makes that
     * an explicit, visible action instead of something that silently happens (or doesn't appear
     * to) during the background check on launch.
     */
    private void requestRoot() {
        rootStatus.setText("Requesting root - check for a Magisk/APatch prompt...");
        new Thread(() -> {
            RootShell.run("id");
            runOnUiThread(this::checkRoot);
        }).start();
    }

    /**
     * silent=true is the automatic launch-time check - stays quiet if already up to date, so it
     * doesn't nag on every open. silent=false is the explicit button, which always says something.
     */
    private void checkForUpdate(boolean manual) {
        if (manual) {
            updateStatus.setVisibility(android.view.View.VISIBLE);
            updateStatus.setText("Checking for updates...");
        }
        new Thread(() -> {
            UpdateChecker.UpdateInfo update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME);
            runOnUiThread(() -> {
                pendingUpdate = update;
                if (update != null) {
                    updateStatus.setVisibility(android.view.View.VISIBLE);
                    updateStatus.setText("Update available: v" + update.version
                            + (update.notes.isEmpty() ? "" : "\n" + update.notes));
                    downloadUpdateButton.setText("Download and Install v" + update.version);
                    downloadUpdateButton.setVisibility(android.view.View.VISIBLE);
                } else if (manual) {
                    updateStatus.setVisibility(android.view.View.VISIBLE);
                    updateStatus.setText("Up to date (v" + BuildConfig.VERSION_NAME + ")");
                    downloadUpdateButton.setVisibility(android.view.View.GONE);
                }
            });
        }).start();
    }

    private void downloadAndInstallUpdate() {
        if (pendingUpdate == null) {
            return;
        }
        updateStatus.setText("Downloading v" + pendingUpdate.version + "...");
        pendingDownloadId = UpdateChecker.startDownload(this, pendingUpdate.downloadUrl, pendingUpdate.version);
        appendLog("Downloading update v" + pendingUpdate.version + " - install prompt will follow.");
    }

    private void applyPercent(int percent) {
        percentSeekBar.setProgress(percent);
        percentLabel.setText(percent + "%");
        new Thread(() -> {
            final RootShell.Result result = fanController.applyPercent(percent);
            runOnUiThread(() -> appendLog(result.success
                    ? "Set " + percent + "% -> ok"
                    : "Set " + percent + "% -> failed: " + result.output));
        }).start();
    }

    private void resetToAuto() {
        new Thread(() -> {
            final RootShell.Result result = fanController.resetToAuto();
            runOnUiThread(() -> appendLog(result.success
                    ? "Reset -> restarted " + fanController.getThermalService() + " + fixed safe duty"
                    : "Reset -> failed: " + result.output));
        }).start();
    }

    private void checkThermalProfile() {
        thermalProfileStatus.setText("Thermal profile: checking...");
        new Thread(() -> {
            final ThermalProfileController.Profile profile = thermalProfileController.readCurrent();
            runOnUiThread(() -> thermalProfileStatus.setText("Thermal profile: " + describeProfile(profile)));
        }).start();
    }

    private void applyThermalProfile(ThermalProfileController.Profile profile) {
        thermalProfileStatus.setText("Thermal profile: configuring " + describeProfile(profile) + "...");
        new Thread(() -> {
            final RootShell.Result result = thermalProfileController.apply(profile);
            runOnUiThread(() -> {
                if (result.success) {
                    appendLog("Thermal profile -> " + describeProfile(profile) + " set for next boot");
                    rebootButton.setVisibility(android.view.View.VISIBLE);
                } else {
                    appendLog("Thermal profile -> failed: " + result.output);
                }
                checkThermalProfile();
            });
        }).start();
    }

    private static String describeProfile(ThermalProfileController.Profile profile) {
        switch (profile) {
            case STOCK:
                return "Stock";
            case PERFORMANCE:
                return "Performance";
            default:
                return "Unknown / custom";
        }
    }

    private void appendLog(String line) {
        logView.setText(line + "\n" + logView.getText());
    }
}
