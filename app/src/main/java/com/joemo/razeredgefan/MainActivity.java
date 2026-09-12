package com.joemo.razeredgefan;

import android.app.Activity;
import android.content.Intent;
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
    private Button grantShizukuButton;
    private FanController fanController;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> runOnUiThread(this::checkRoot);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fanController = new FanController(this);

        rootStatus = findViewById(R.id.root_status);
        pathStatus = findViewById(R.id.path_status);
        percentLabel = findViewById(R.id.percent_label);
        logView = findViewById(R.id.log_view);
        percentSeekBar = findViewById(R.id.percent_seekbar);
        grantShizukuButton = findViewById(R.id.grant_shizuku_button);

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

        findViewById(R.id.discover_button).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DiscoveryActivity.class)));

        findViewById(R.id.apply_button).setOnClickListener(v ->
                applyPercent(percentSeekBar.getProgress()));

        ((Button) findViewById(R.id.preset_quiet)).setOnClickListener(v -> applyPercent(20));
        ((Button) findViewById(R.id.preset_balanced)).setOnClickListener(v -> applyPercent(50));
        ((Button) findViewById(R.id.preset_performance)).setOnClickListener(v -> applyPercent(80));
        ((Button) findViewById(R.id.preset_max)).setOnClickListener(v -> applyPercent(100));

        findViewById(R.id.reset_auto_button).setOnClickListener(v -> resetToAuto());

        grantShizukuButton.setOnClickListener(v -> ShizukuBackend.requestPermission());

        Shizuku.addRequestPermissionResultListener(permissionListener);
        checkRoot();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
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
                        rootStatus.setText("No privileged access - start Shizuku or grant su.");
                        grantShizukuButton.setVisibility(android.view.View.GONE);
                }
            });
        }).start();
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

    private void appendLog(String line) {
        logView.setText(line + "\n" + logView.getText());
    }
}
