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

        // The graph's post-processing op returns boxes, classes, scores and a count.
        // Each destination array MUST match its tensor's exact shape, so they are
        // allocated from the shapes the interpreter reports rather than assumed.
        Map<Integer, Object> outputs = new HashMap<Integer, Object>();
        int boxesIndex = -1;
        int classesIndex = -1;
        int scoresIndex = -1;
        int countIndex = -1;

        int outputCount = interpreter.getOutputTensorCount();
        for (int i = 0; i < outputCount; i++) {
            int[] shape = interpreter.getOutputTensor(i).shape();
            outputs.put(i, allocateForShape(shape));
            if (shape.length == 3 && boxesIndex < 0) {
                boxesIndex = i;
            } else if (shape.length == 2 && classesIndex < 0) {
                classesIndex = i;
            } else if (shape.length == 2 && scoresIndex < 0) {
                scoresIndex = i;
            } else if (shape.length <= 1 && countIndex < 0) {
                countIndex = i;
            }
        }
        if (boxesIndex < 0 || classesIndex < 0 || scoresIndex < 0) {
            return results;   // not the model we expect; nothing to draw
        }

        interpreter.runForMultipleInputsOutputs(new Object[]{buffer}, outputs);

        float[][][] boxes = (float[][][]) outputs.get(boxesIndex);
        float[][] classes = (float[][]) outputs.get(classesIndex);
        float[][] scores = (float[][]) outputs.get(scoresIndex);

        int found = Math.min(boxes[0].length, scores[0].length);
        if (countIndex >= 0) {
            Object countOut = outputs.get(countIndex);
            float declared = MAX_DETECTIONS;
            if (countOut instanceof float[]) {
                float[] flat = (float[]) countOut;
                if (flat.length > 0) {
                    declared = flat[0];
                }
            } else if (countOut instanceof float[][]) {
                float[][] nested = (float[][]) countOut;
                if (nested.length > 0 && nested[0].length > 0) {
                    declared = nested[0][0];
                }
            }
            found = Math.min(found, Math.max(0, (int) declared));
        }

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

        // The model can return several overlapping boxes for the same item.
        // Keep the best one per object, but still allow two dogs side by side.
        List<Detection> unique = new ArrayList<Detection>();
        for (int i = 0; i < results.size(); i++) {
            Detection candidate = results.get(i);
            boolean duplicate = false;
            for (int j = 0; j < unique.size(); j++) {
                Detection kept = unique.get(j);
                if (kept.label.equals(candidate.label)
                        && intersectionOverUnion(kept.box, candidate.box) > 0.10f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    private static float intersectionOverUnion(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0f, right - left);
        float interH = Math.max(0f, bottom - top);
        float intersection = interW * interH;
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    /** Allocates a float array whose shape matches the tensor exactly. */
    private static Object allocateForShape(int[] shape) {
        int a = shape.length > 0 ? Math.max(shape[0], 1) : 1;
        int b = shape.length > 1 ? Math.max(shape[1], 1) : 1;
        int c = shape.length > 2 ? Math.max(shape[2], 1) : 1;
        int d = shape.length > 3 ? Math.max(shape[3], 1) : 1;
        switch (shape.length) {
            case 0:
                return new float[1];
            case 1:
                return new float[a];
            case 2:
                return new float[a][b];
            case 3:
                return new float[a][b][c];
            default:
                return new float[a][b][c][d];
        }
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
