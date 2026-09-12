package com.joemo.razeredgefan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView rootStatus;
    private TextView pathStatus;
    private TextView percentLabel;
    private TextView logView;
    private SeekBar percentSeekBar;
    private FanController fanController;

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

        checkRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPathStatus();
    }

    private void refreshPathStatus() {
        if (fanController.isConfigured()) {
            pathStatus.setText("Node: " + fanController.getPath()
                    + "  (0-" + fanController.getMaxState() + ")");
        } else {
            pathStatus.setText("No fan node configured yet - tap Discover Fan Nodes.");
        }
    }

    private void checkRoot() {
        new Thread(() -> {
            final boolean hasRoot = RootShell.isRootAvailable();
            runOnUiThread(() -> rootStatus.setText(hasRoot
                    ? "Root access: granted"
                    : "Root access: NOT granted - grant su to this app first."));
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
                    ? "Reset to auto -> ok (" + fanController.getThermalService() + " restarted)"
                    : "Reset to auto -> failed: " + result.output));
        }).start();
    }

    private void appendLog(String line) {
        logView.setText(line + "\n" + logView.getText());
    }
}
