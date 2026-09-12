package com.joemo.razeredgefan;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryActivity extends Activity {

    private ListView nodeList;
    private TextView emptyView;
    private ArrayAdapter<SysfsScanner.Node> adapter;
    private final List<SysfsScanner.Node> nodes = new ArrayList<>();
    private FanController fanController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        fanController = new FanController(this);
        nodeList = findViewById(R.id.node_list);
        emptyView = findViewById(R.id.empty_view);
        Button scanButton = findViewById(R.id.scan_button);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nodes);
        nodeList.setAdapter(adapter);

        nodeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onNodePicked(nodes.get(position));
            }
        });

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runScan();
            }
        });

        runScan();
    }

    private void runScan() {
        emptyView.setText("Scanning...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<SysfsScanner.Node> found = SysfsScanner.scan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        nodes.clear();
                        nodes.addAll(found);
                        adapter.notifyDataSetChanged();
                        emptyView.setText(found.isEmpty()
                                ? "No candidates found. Root may be missing, or this device exposes fan control differently."
                                : found.size() + " candidate node(s) found - tap one to use it.");
                    }
                });
            }
        }).start();
    }

    private void onNodePicked(final SysfsScanner.Node node) {
        int suggestedMax = node.maxState > 0 ? node.maxState : 255;
        new AlertDialog.Builder(this)
                .setTitle("Use this node?")
                .setMessage(node.path + "\n\nMax value: " + suggestedMax
                        + "\n\nThe app will map its 0-100% slider onto 0-" + suggestedMax + " for this node.")
                .setPositiveButton("Use it", (dialog, which) -> {
                    fanController.configure(node.path, suggestedMax);
                    Toast.makeText(this, "Fan node configured", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
