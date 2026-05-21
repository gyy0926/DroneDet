package com.win.detect;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.win.detect.collect.WifiCSICollector;
import com.win.detect.model.DataCollectModel;

import java.lang.ref.WeakReference;

public class FragmentCsiDataCollect extends Fragment {

    private AlertDialog collectDialog;
    private TextView tvTimer;
    private volatile boolean isCollecting = false;
    private volatile boolean userStopped = false;
    private final MyHandler handler = new MyHandler(this);
    private Thread timerThread;

    private static class MyHandler extends Handler {
        private final WeakReference<FragmentCsiDataCollect> fragmentRef;

        public MyHandler(FragmentCsiDataCollect fragment) {
            super(Looper.getMainLooper());
            fragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            FragmentCsiDataCollect fragment = fragmentRef.get();
            if (fragment == null || fragment.tvTimer == null) return;
            if (msg.what == 0) {
                fragment.tvTimer.setText("Collected: " + msg.arg1 + " s");
            }
        }
    }

    public FragmentCsiDataCollect() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_csi_data_collect, container, false);
        Button btnCollect = view.findViewById(R.id.btn_collect);
        btnCollect.setOnClickListener(v -> showCollectDialog());
        return view;
    }

    /**
     * 输入参数弹窗（修复时间转换异常）
     */
    private void showCollectDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_collect, null);
        EditText etCSI = view.findViewById(R.id.et_csi);
        EditText etName = view.findViewById(R.id.et_name);
        EditText etTime = view.findViewById(R.id.et_time);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Data Collect")
                .setView(view)
                .setPositiveButton("START COLLECT", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setOnClickListener(v -> {
                String csi = etCSI.getText().toString().trim();
                String name = etName.getText().toString().trim();
                String timeStr = etTime.getText().toString().trim();

                if (csi.isEmpty() || name.isEmpty() || timeStr.isEmpty()) {
                    showToastSafely("Input invalid", Toast.LENGTH_SHORT);
                    return;
                }

                int time;
                try {
                    time = Integer.parseInt(timeStr);
                } catch (NumberFormatException e) {
                    showToastSafely("Time must be a number", Toast.LENGTH_SHORT);
                    return;
                }
                if (time <= 0) {
                    showToastSafely("Time must be > 0", Toast.LENGTH_SHORT);
                    return;
                }

                DataCollectModel model = new DataCollectModel();
                model.CSIParam = csi;
                model.WifiName = name;
                model.CollectTime = time;

                startCollect(model);
                dialog.dismiss();
            });
        }
    }

    /**
     * 启动采集（修复计时器中断）
     */
    private void startCollect(DataCollectModel model) {
        if (isCollecting) {
            showToastSafely("Already collecting", Toast.LENGTH_SHORT);
            return;
        }

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_collecting, null);
        tvTimer = view.findViewById(R.id.tv_timer);
        Button btnStop = view.findViewById(R.id.btn_stop);

        collectDialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(false)
                .show();

        isCollecting = true;
        userStopped = false;

        btnStop.setOnClickListener(v -> {
            userStopped = true;
            WifiCSICollector.stop();
            if (timerThread != null) {
                timerThread.interrupt();
                timerThread = null;
            }
            if (collectDialog != null) collectDialog.dismiss();
            isCollecting = false;
            showToastSafely("Collect stopped", Toast.LENGTH_LONG);
        });

        WifiCSICollector.startCollect(requireContext(), model, new WifiCSICollector.CollectListener() {
            @Override
            public void onStart() {
                startTimer(model.CollectTime);
            }

            @Override
            public void onFinish() {
                handler.post(() -> {
                    if (userStopped) return;
                    if (collectDialog != null) collectDialog.dismiss();
                    isCollecting = false;
                    if (timerThread != null) {
                        timerThread.interrupt();
                        timerThread = null;
                    }
                    showToastSafely("Collect success", Toast.LENGTH_LONG);
                });
            }

            @Override
            public void onError(String msg) {
                handler.post(() -> {
                    if (userStopped) return;
                    if (collectDialog != null) collectDialog.dismiss();
                    isCollecting = false;
                    if (timerThread != null) {
                        timerThread.interrupt();
                        timerThread = null;
                    }
                    showToastSafely(msg, Toast.LENGTH_LONG);
                });
            }
        });
    }

    /**
     * 计时器（修复逻辑错误+支持中断）
     */
    private void startTimer(int total) {
        if (timerThread != null) timerThread.interrupt();
        timerThread = new Thread(() -> {
            int t = 0;
            try {
                while (t < total && isCollecting) {
                    Message msg = handler.obtainMessage(0);
                    msg.arg1 = t;
                    handler.sendMessage(msg);

                    Thread.sleep(1000);
                    t++;
                }
                if (isCollecting) {
                    Message msg = handler.obtainMessage(0);
                    msg.arg1 = total;
                    handler.sendMessage(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        timerThread.start();
    }

    private void showToastSafely(String text, int duration) {
        if (!isAdded()) return;
        if (getContext() == null) return;
        Toast.makeText(getContext(), text, duration).show();
    }

    /**
     * 销毁时清理资源（修复内存泄漏）
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isCollecting) WifiCSICollector.stop();
        if (collectDialog != null && collectDialog.isShowing()) collectDialog.dismiss();
        if (timerThread != null) {
            timerThread.interrupt();
            timerThread = null;
        }
        handler.removeCallbacksAndMessages(null);
        tvTimer = null;
        collectDialog = null;
    }
}

