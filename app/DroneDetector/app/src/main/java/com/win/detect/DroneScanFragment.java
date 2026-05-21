package com.win.detect;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.win.detect.adapter.CSIAdapter;
import com.win.detect.collect.CSICollector;
import com.win.detect.detect.WifiOutputParser;
import com.win.detect.detect.WifiScanExecutor;
import com.win.detect.filter.WifiFilter;
import com.win.detect.model.WifiCSI;
import com.win.detect.model.WifiInfo;

import java.util.List;

public class DroneScanFragment extends Fragment {

    private RecyclerView recycler;

    private AlertDialog locatingDialog;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_drone_scan, container, false);
        recycler = v.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        v.findViewById(R.id.btn_test).setOnClickListener(btn -> scanWifi());
        return v;
    }

    private void scanWifi() {
        try {
            String output = WifiScanExecutor.scanWifi();
            List<WifiInfo> infos = WifiOutputParser.parse(output);
            infos = WifiFilter.filterWifi(infos);

            List<WifiCSI> csiList = CSIAdapter.generateCSIParams(infos);
//            csiList.add(new WifiCSI("RightWifi", "DRABEwAAAQBgYB+RKdMAAAAAAAAAAAAAAAAAAAAAAAAAAA=="));

            if (csiList == null || csiList.isEmpty()) {
                Toast.makeText(getContext(), "No valid WiFi signals detected. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(getContext(), "Scanning & detecting...", Toast.LENGTH_SHORT).show();
            CSICollector.collectAsync(csiList, new CSICollector.UiCallback() {
                @Override
                public void onDroneDetected(String wifiName) {
                    showDetectHintDialog();
                }

                @Override
                public void onNoDroneDetected() {
                    showNoDroneDialog();
                }

                @Override
                public void onLocateError(String message) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onLocateResult(float distance) {
                    showResultDialog(distance);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            if (getContext() != null) {
                Toast.makeText(getContext(), "Scan failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showNoDroneDialog() {
        if (getContext() == null) return;
        NoDroneHintView view = new NoDroneHintView(getContext());
        new AlertDialog.Builder(getContext())
                .setTitle("INFO")
                .setView(view)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDetectHintDialog() {
        if (getContext() == null) return;
        if (locatingDialog != null && locatingDialog.isShowing()) return;

        DetectHintView hintView = new DetectHintView(getContext());
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("WARNING!")
                .setView(hintView)
                .setNegativeButton("START LOCATING", null)
                .setPositiveButton("POSITION RESULT", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnStart = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button btnResult = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnResult.setEnabled(false);

            btnStart.setOnClickListener(v -> {
                btnStart.setEnabled(false);
                btnResult.setEnabled(true);
                Toast.makeText(getContext(), "Locating started (auto stop in 3s)...", Toast.LENGTH_SHORT).show();
                CSICollector.startLocatingAsync(new CSICollector.UiCallback() {
                    @Override
                    public void onDroneDetected(String wifiName) {
                    }

                    @Override
                    public void onNoDroneDetected() {
                    }

                    @Override
                    public void onLocateError(String message) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        }
                        btnStart.setEnabled(true);
                    }

                    @Override
                    public void onLocateResult(float distance) {
                    }
                });
            });

            btnResult.setOnClickListener(v -> {
                btnResult.setEnabled(false);
                Toast.makeText(getContext(), "Calculating position...", Toast.LENGTH_SHORT).show();
                CSICollector.predictLocateAsync(new CSICollector.UiCallback() {
                    @Override
                    public void onDroneDetected(String wifiName) {
                    }

                    @Override
                    public void onNoDroneDetected() {
                    }

                    @Override
                    public void onLocateError(String message) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        }
                        btnResult.setEnabled(true);
                    }

                    @Override
                    public void onLocateResult(float distance) {
                        dialog.dismiss();
                        showResultDialog(distance);
                    }
                });
            });
        });

        dialog.show();
        locatingDialog = dialog;
    }

    private void showResultDialog(float distance) {
        if (getContext() == null) return;
        DronePositionView view = new DronePositionView(getContext(), distance);
        new AlertDialog.Builder(getContext())
                .setTitle("Drone Positioning Result:")
                .setView(view)
                .setPositiveButton("Confirm", null)
                .show();
    }
}

