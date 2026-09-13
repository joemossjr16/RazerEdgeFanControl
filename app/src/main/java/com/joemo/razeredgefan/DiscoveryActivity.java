package com.joemo.razeredgefan;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class DiscoveryActivity extends Activity {

    private FanController fanController;
    private EditText busInput;
    private EditText addrInput;
    private EditText regInput;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        UiAnim.enter(findViewById(R.id.discovery_root));
        UiAnim.breathe(findViewById(R.id.discovery_title));

        fanController = new FanController(this);
        busInput = findViewById(R.id.bus_input);
        addrInput = findViewById(R.id.addr_input);
        regInput = findViewById(R.id.reg_input);
        statusView = findViewById(R.id.status_view);

        busInput.setText(String.valueOf(fanController.getBus()));
        addrInput.setText(fanController.getAddr());
        regInput.setText(fanController.getReg());

        UiAnim.punch(findViewById(R.id.save_button));
        UiAnim.punch(findViewById(R.id.refresh_button));
        findViewById(R.id.save_button).setOnClickListener(v -> save());
        findViewById(R.id.refresh_button).setOnClickListener(v -> refresh());

        refresh();
    }

    private void save() {
        try {
            int bus = Integer.parseInt(busInput.getText().toString().trim());
            String addr = addrInput.getText().toString().trim();
            String reg = regInput.getText().toString().trim();
            fanController.configureTarget(bus, addr, reg);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Bus must be a number", Toast.LENGTH_SHORT).show();
        }
    }

    private void refresh() {
        statusView.setText("Reading...");
        new Thread(() -> {
            final RootShell.Result duty = fanController.readRawDuty();
            final RootShell.Result rpm = fanController.readRpm();
            runOnUiThread(() -> statusView.setText(
                    "PWMR raw: " + (duty.success ? duty.output : "error: " + duty.output) + "\n"
                            + "RPM: " + (rpm.success ? rpm.output : "error: " + rpm.output)));
        }).start();
    }
}
