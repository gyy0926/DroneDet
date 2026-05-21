package com.win.detect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class DetectHintView extends View {

    private Paint textPaint;

    public DetectHintView(Context context) {
        super(context);
        init();
    }

    private void init() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(50);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = 420; // 经验值，刚好放下 4 行大字
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float x = 60;
        float y = 80;

        canvas.drawText("Drone detected behind you!", x, y, textPaint);
        y += 90;
        canvas.drawText("1. Press 'START LOCATING'", x, y, textPaint);
        y += 80;
        canvas.drawText("2. Walk forward ~1.5 m normally", x, y, textPaint);
        y += 80;
        canvas.drawText("3. Press 'POSITION RESULT'", x, y, textPaint);
    }
}
