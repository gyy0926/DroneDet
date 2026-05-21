package com.win.detect;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.win.detect.model.WifiInfo;

import java.util.List;

public class WifiInfoAdapter extends RecyclerView.Adapter<WifiInfoAdapter.Holder> {

    private List<WifiInfo> list;

    public WifiInfoAdapter(List<WifiInfo> list) {
        this.list = list;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wifi_info, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(Holder h, int i) {
        WifiInfo w = list.get(i);

        h.ssid.setText(
                (w.name == null || w.name.isEmpty()) ? "<隐藏SSID>" : w.name
        );

        h.detail.setText(
                "MAC: " + w.mac +
                        "\n信道: " + w.channel +
                        "  频率: " + w.frequency + " MHz" +
                        "\n带宽: " + w.channelWidth + " MHz" +
                        "  信号强度: " + w.signalIntensity + " dBm"
        );
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView ssid, detail;

        Holder(View v) {
            super(v);
            ssid = v.findViewById(R.id.tv_ssid);
            detail = v.findViewById(R.id.tv_detail);
        }
    }
}
