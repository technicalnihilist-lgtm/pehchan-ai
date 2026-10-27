package com.rajok.pehchanai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device object detector (SSD MobileNet, COCO 80 classes).
 *
 * Finds and localises the things in a photo: people, animals, food, phones,
 * laptops, furniture, vehicles, kitchen appliances and so on. The model has
 * non-max suppression built in, so it returns at most 10 finished boxes.
 */
public class Detector {

    public static final int INPUT_SIZE = 300;
    public static final int MAX_DETECTIONS = 10;
    public static final float MIN_SCORE = 0.42f;

    public static class Detection {
        public final String label;
        public final float score;
        /** Normalised box: left, top, right, bottom in 0..1 of the image. */
        public final RectF box;

        Detection(String label, float score, RectF box) {
            this.label = label;
            this.score = score;
            this.box = box;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels;

    public Detector(Context context) throws IOException {
        labels = readLabels(context, "coco_labels.txt");
        if (labels.isEmpty()) {
            throw new IOException("coco labels missing");
        }
        ByteBuffer model = readAssetAsBuffer(context, "detect.tflite");
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        interpreter = new Interpreter(model, options);
    }

    private static ByteBuffer readAssetAsBuffer(Context context, String name) throws IOException {
        InputStream is = context.getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(5 * 1024 * 1024);
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = is.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            byte[] bytes = out.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.rewind();
            return buffer;
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static List<String> readLabels(Context context, String name) throws IOException {
        InputStream is = context.getAssets().open(name);
        List<String> lines = new ArrayList<String>(100);
        try {
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    lines.add(line);
                }
            }
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
        return lines;
    }

    /** Runs the detector and returns the boxes, best first. */
    public List<Detection> detect(Bitmap source) {
        List<Detection> results = new ArrayList<Detection>();
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) {
            return results;
        }

        Bitmap input = source;
        boolean scaled = false;
        if (source.getWidth() != INPUT_SIZE || source.getHeight() != INPUT_SIZE) {
            input = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true);
            scaled = true;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
                .order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        input.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            buffer.put((byte) ((p >> 16) & 0xFF));
            buffer.put((byte) ((p >> 8) & 0xFF));
            buffer.put((byte) (p & 0xFF));
        }
        buffer.rewind();
        if (scaled) {
            input.recycle();
        }

        // The graph's post-processing op returns: boxes [1,10,4], classes [1,10],
        // scores [1,10], count [1]. Discover them by shape so a model swap is safe.
        float[][][] boxes = new float[1][MAX_DETECTIONS][4];
        float[][] classes = new float[1][MAX_DETECTIONS];
        float[][] scores = new float[1][MAX_DETECTIONS];
        float[][] count = new float[1][1];

        Map<Integer, Object> outputs = new HashMap<Integer, Object>();
        boolean gotClasses = false;
        int outputCount = interpreter.getOutputTensorCount();
        for (int i = 0; i < outputCount; i++) {
            int[] shape = interpreter.getOutputTensor(i).shape();
            if (shape.length == 3) {
                outputs.put(i, boxes);
            } else if (shape.length == 2) {
                if (!gotClasses) {
                    outputs.put(i, classes);
                    gotClasses = true;
                } else {
                    outputs.put(i, scores);
                }
            } else {
                outputs.put(i, count);
            }
        }

        interpreter.runForMultipleInputsOutputs(new Object[]{buffer}, outputs);

        int found = Math.min(MAX_DETECTIONS, Math.max(0, (int) count[0][0]));
        for (int i = 0; i < found; i++) {
            float score = scores[0][i];
            if (score < MIN_SCORE) {
                continue;
            }
            int index = (int) classes[0][i];
            if (index < 0 || index >= labels.size()) {
                continue;
            }
            String label = labels.get(index);
            if (label == null || label.length() == 0 || label.equals("???")) {
                continue;
            }
            float top = clamp(boxes[0][i][0]);
            float left = clamp(boxes[0][i][1]);
            float bottom = clamp(boxes[0][i][2]);
            float right = clamp(boxes[0][i][3]);
            if (right - left < 0.02f || bottom - top < 0.02f) {
                continue;
            }
            results.add(new Detection(label, score, new RectF(left, top, right, bottom)));
        }

        java.util.Collections.sort(results, new java.util.Comparator<Detection>() {
            @Override
            public int compare(Detection a, Detection b) {
                return Float.compare(b.score, a.score);
            }
        });
        return results;
    }

    private static float clamp(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    public void close() {
        try {
            interpreter.close();
        } catch (Throwable ignored) {
        }
    }
}
