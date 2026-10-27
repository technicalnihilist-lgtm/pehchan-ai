package com.rajok.pehchanai;

import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * On-device image classifier.
 * Wraps the bundled EfficientNet-Lite0 (int8, 224x224) TFLite model and the
 * 1000 ImageNet class labels. Everything runs offline on the phone CPU.
 */
public class Classifier {

    public static final int INPUT_SIZE = 224;
    public static final int MAX_RESULTS = 5;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final float[] scores;

    public static class Result {
        public final String label;      // pretty label, e.g. "golden retriever"
        public final String rawLabel;   // full label as stored, e.g. "golden retriever"
        public final float score;       // 0..1

        Result(String label, String rawLabel, float score) {
            this.label = label;
            this.rawLabel = rawLabel;
            this.score = score;
        }
    }

    public Classifier(Context context) throws IOException {
        ByteBuffer model = readAssetAsBuffer(context, "model.tflite");
        labels = readAssetLines(context, "labels.txt");
        if (labels.isEmpty()) {
            throw new IOException("labels missing");
        }
        scores = new float[labels.size()];

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        interpreter = new Interpreter(model, options);
    }

    private static ByteBuffer readAssetAsBuffer(Context context, String name) throws IOException {
        InputStream is = context.getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024 * 1024);
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

    private static List<String> readAssetLines(Context context, String name) throws IOException {
        InputStream is = context.getAssets().open(name);
        List<String> lines = new ArrayList<String>(1100);
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

    /** Runs the model on the given bitmap and returns the top matches, best first. */
    public List<Result> classify(Bitmap source) {
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

        Tensor outputTensor = interpreter.getOutputTensor(0);
        DataType type = outputTensor.dataType();
        int classes = labels.size();

        if (type == DataType.FLOAT32) {
            float[][] out = new float[1][classes];
            interpreter.run(buffer, out);
            int n = Math.min(classes, out[0].length);
            System.arraycopy(out[0], 0, scores, 0, n);
        } else {
            byte[][] out = new byte[1][classes];
            interpreter.run(buffer, out);
            Tensor.QuantizationParams params = outputTensor.quantizationParams();
            float scale = params.getScale();
            int zeroPoint = params.getZeroPoint();
            if (scale <= 0f) {
                scale = 1f / 256f;
            }
            boolean signed = (type == DataType.INT8);
            for (int i = 0; i < classes; i++) {
                int v = signed ? (int) out[0][i] : (out[0][i] & 0xFF);
                scores[i] = (v - zeroPoint) * scale;
            }
        }

        List<Result> results = new ArrayList<Result>();
        boolean[] used = new boolean[classes];
        for (int k = 0; k < MAX_RESULTS; k++) {
            int best = -1;
            float bestScore = -1f;
            for (int i = 0; i < classes; i++) {
                if (!used[i] && scores[i] > bestScore) {
                    bestScore = scores[i];
                    best = i;
                }
            }
            if (best < 0 || bestScore <= 0.008f) {
                break;
            }
            used[best] = true;
            String raw = labels.get(best);
            results.add(new Result(pretty(raw), raw, bestScore));
        }

        Collections.sort(results, new Comparator<Result>() {
            @Override
            public int compare(Result a, Result b) {
                return Float.compare(b.score, a.score);
            }
        });
        return results;
    }

    /** "tabby, tabby cat" -> "tabby cat"; "golden_retriever" -> "golden retriever". */
    public static String pretty(String raw) {
        String s = raw.replace('_', ' ').trim();
        int comma = s.indexOf(',');
        if (comma > 0) {
            String first = s.substring(0, comma).trim();
            String second = s.substring(comma + 1).trim();
            // prefer the longer, more descriptive variant when it adds words
            if (second.length() > first.length()) {
                s = second;
            } else {
                s = first;
            }
        }
        return s;
    }

    public void close() {
        try {
            interpreter.close();
        } catch (Throwable ignored) {
        }
    }
}
