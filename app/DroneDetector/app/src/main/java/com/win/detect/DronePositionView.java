package com.win.detect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.View;

public class DronePositionView extends View {

    private static final float H = 13.55f;
    private static float CD = 13.24f;

    private Paint linePaint;
    private Paint textPaint;

    private Bitmap droneBitmap;
    private Bitmap personBitmap;

    private int horizontalDist;
    private int altitudeHeight;

    public DronePositionView(Context context, float cd) {
        super(context);
        this.CD = cd;
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(7);   // 更粗
        linePaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(50);

        droneBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.drone);
        personBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.person);


    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int width = MeasureSpec.getSize(widthMeasureSpec);

        // ===== 世界几何比例 =====
        float worldW = CD;   // 水平距离
        float worldH = H;    // 高度
        float geometryRatio = worldH / worldW;

        // ===== UI 参数 =====
        int textAreaHeight = 140;   // 上方文字
        int marginTop = 60;
        int marginBottom = 60;

        // 几何绘图高度（按比例）
        int geometryHeight = (int)(width * geometryRatio);

        // 原本总高度
        int desiredHeight = textAreaHeight + marginTop + geometryHeight + marginBottom;

        // 限制最大高度为屏幕高度的 2/3
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxHeight = (int)(dm.heightPixels * 2f / 5f);

        int finalHeight = Math.min(desiredHeight, maxHeight);

        setMeasuredDimension(width, finalHeight);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        float margin = 80f;

        float worldW = CD;
        float worldH = H;

        float scale = Math.min(
                (w - 2 * margin) / worldW,
                (h - 2 * margin) / worldH
        );

        // 实际几何尺寸
        float geoW = worldW * scale;
        float geoH = worldH * scale;

// 居中起点
        float startX = (w - geoW) / 2f;
        float startY = (h - geoH) / 2f + geoH; // 底部对齐地面

        float cx = startX;
        float cy = startY;

        float dx = cx + geoW;
        float dy = cy;

        float ax = dx;
        float ay = cy - geoH;


        // ===== 文字警告（代替 setMessage）=====
//        canvas.drawText("身后无人机示意图：", margin, 45, textPaint);
        canvas.drawText(
                String.format("Altitude: %.2f m", (float) H),
                margin, 50, textPaint
        );

        canvas.drawText(
                String.format("Distance: %.2f m",  CD),
                margin, 110, textPaint
        );


        // ===== 地面 =====
        canvas.drawLine(cx, cy, dx, dy, linePaint);

        // ===== 几何线 =====
        canvas.drawLine(ax, ay, dx, dy, linePaint); // 高度
        canvas.drawLine(cx, cy, ax, ay, linePaint); // 斜距

        // ===== 高度说明（分行）=====
//        canvas.drawText("高度", ax + 20, (ay + dy) / 2 - 20, textPaint);
//        canvas.drawText("10 m", ax + 20, (ay + dy) / 2 + 20, textPaint);

        // ===== 人物 =====
        Bitmap personScaled = Bitmap.createScaledBitmap(personBitmap, 90, 130, true);
        canvas.drawBitmap(
                personScaled,
                cx - personScaled.getWidth() / 2,
                cy - personScaled.getHeight(),
                null
        );

        // ===== 无人机（放大 2 倍）=====
        Bitmap droneScaled = Bitmap.createScaledBitmap(droneBitmap, 250, 250, true);
        canvas.drawBitmap(
                droneScaled,
                ax - droneScaled.getWidth() / 2,
                ay - droneScaled.getHeight() / 2,
                null
        );
    }
}
