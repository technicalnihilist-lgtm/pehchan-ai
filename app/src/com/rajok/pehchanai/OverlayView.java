package com.rajok.pehchanai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the detector's boxes straight on top of the photo. The preview uses
 * centre-crop, so the same transform is applied here to keep boxes glued to
 * the right pixels.
 */
public class OverlayView extends View {

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Detector.Detection> detections = new ArrayList<Detector.Detection>();
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int highlighted = -1;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        float d = getResources().getDisplayMetrics().density;
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.2f * d);
        boxPaint.setColor(Color.parseColor("#A78BFA"));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#337C5CFF"));

        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(Color.parseColor("#E6121A2C"));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(10.5f * d);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setResults(List<Detector.Detection> results, int imageWidth, int imageHeight) {
        this.detections = results == null ? new ArrayList<Detector.Detection>() : results;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.highlighted = -1;
        invalidate();
    }

    public void setHighlighted(int index) {
        this.highlighted = index;
        invalidate();
    }

    public void clear() {
        detections = new ArrayList<Detector.Detection>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();
        // centre-crop: fill the view, crop the overflow, keep it centred
        float scale = Math.max(viewW / imageWidth, viewH / imageHeight);
        float drawnW = imageWidth * scale;
        float drawnH = imageHeight * scale;
        float offsetX = (viewW - drawnW) / 2f;
        float offsetY = (viewH - drawnH) / 2f;

        float d = getResources().getDisplayMetrics().density;
        float pad = 3f * d;

        for (int i = 0; i < detections.size(); i++) {
            Detector.Detection det = detections.get(i);
            RectF r = new RectF(
                    offsetX + det.box.left * drawnW,
                    offsetY + det.box.top * drawnH,
                    offsetX + det.box.right * drawnW,
                    offsetY + det.box.bottom * drawnH);

            boolean active = (i == highlighted);
            boxPaint.setColor(active ? Color.parseColor("#4ADE80") : Color.parseColor("#A78BFA"));
            boxPaint.setStrokeWidth((active ? 3.0f : 2.2f) * d);
            fillPaint.setColor(active ? Color.parseColor("#334ADE80") : Color.parseColor("#2A7C5CFF"));

            canvas.drawRoundRect(r, 10f * d, 10f * d, fillPaint);
            canvas.drawRoundRect(r, 10f * d, 10f * d, boxPaint);

            String text = det.label + "  " + Math.round(det.score * 100) + "%";
            float textWidth = textPaint.measureText(text);
            float chipH = textPaint.getTextSize() + 7f * d;
            float chipW = textWidth + 12f * d;

            float chipLeft = r.left;
            float chipTop = r.top - chipH - 2f * d;
            if (chipTop < 0) {
                chipTop = r.top + 2f * d;   // no room above the box
            }
            if (chipLeft + chipW > viewW) {
                chipLeft = Math.max(0, viewW - chipW - pad);
            }
            if (chipLeft < pad) {
                chipLeft = pad;
            }
            RectF chip = new RectF(chipLeft, chipTop, chipLeft + chipW, chipTop + chipH);
            canvas.drawRoundRect(chip, 7f * d, 7f * d, chipPaint);
            canvas.drawText(text, chip.left + 6f * d,
                    chip.bottom - textPaint.getTextSize() * 0.28f - 2f * d, textPaint);
        }
    }
}
