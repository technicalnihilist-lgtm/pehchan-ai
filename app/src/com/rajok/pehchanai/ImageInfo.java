package com.rajok.pehchanai;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cheap, dependency-free image "facts": size, file weight, brightness and the
 * dominant colours of the photo. Shown to the user under the AI result so the
 * screen always explains something useful, even for an unusual photo.
 */
public class ImageInfo {

    public int width;
    public int height;
    public long bytes;
    public String mime = "image/*";
    public String brightness = "Normal";
    public final List<Integer> colors = new ArrayList<Integer>();

    public static ImageInfo analyze(Bitmap bitmap, int width, int height, long bytes, String mime) {
        ImageInfo info = new ImageInfo();
        info.width = width;
        info.height = height;
        info.bytes = bytes;
        if (mime != null && mime.length() > 0) {
            info.mime = mime;
        }
        if (bitmap != null) {
            extractColorsAndBrightness(bitmap, info);
        }
        return info;
    }

    private static void extractColorsAndBrightness(Bitmap bitmap, ImageInfo info) {
        int w = 44;
        int h = Math.max(1, Math.round(44f * bitmap.getHeight() / (float) bitmap.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(bitmap, w, h, true);
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        if (small != bitmap) {
            small.recycle();
        }

        double luminanceSum = 0;
        Map<Integer, int[]> buckets = new HashMap<Integer, int[]>();
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = Color.alpha(p);
            if (a < 32) {
                continue;
            }
            int r = Color.red(p);
            int g = Color.green(p);
            int b = Color.blue(p);
            luminanceSum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;

            int key = ((r >> 5) << 10) | ((g >> 5) << 5) | (b >> 5);
            int[] acc = buckets.get(key);
            if (acc == null) {
                acc = new int[]{0, 0, 0, 0};
                buckets.put(key, acc);
            }
            acc[0] += r;
            acc[1] += g;
            acc[2] += b;
            acc[3] += 1;
        }

        double avgLum = pixels.length > 0 ? luminanceSum / pixels.length : 0.5;
        if (avgLum > 0.62) {
            info.brightness = "Bright";
        } else if (avgLum > 0.34) {
            info.brightness = "Balanced";
        } else {
            info.brightness = "Dark";
        }

        List<int[]> sorted = new ArrayList<int[]>();
        for (Iterator<Map.Entry<Integer, int[]>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, int[]> entry = it.next();
            sorted.add(entry.getValue());
        }
        java.util.Collections.sort(sorted, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[3] - a[3];
            }
        });

        for (int i = 0; i < sorted.size() && info.colors.size() < 5; i++) {
            int[] acc = sorted.get(i);
            int count = acc[3];
            if (count < 3) {
                continue;
            }
            int r = acc[0] / count;
            int g = acc[1] / count;
            int b = acc[2] / count;
            boolean tooClose = false;
            for (int existing : info.colors) {
                if (distance(existing, r, g, b) < 48) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                info.colors.add(Color.rgb(r, g, b));
            }
        }
        if (info.colors.isEmpty()) {
            info.colors.add(Color.rgb(120, 120, 130));
        }
    }

    private static double distance(int color, int r, int g, int b) {
        double dr = Color.red(color) - r;
        double dg = Color.green(color) - g;
        double db = Color.blue(color) - b;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    public String dimensionText() {
        return width + " × " + height + " px";
    }

    public String sizeText() {
        if (bytes <= 0) {
            return "Unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    public String formatText() {
        String m = mime.toLowerCase(Locale.US);
        if (m.contains("jpeg") || m.contains("jpg")) {
            return "JPEG photo";
        }
        if (m.contains("png")) {
            return "PNG image";
        }
        if (m.contains("webp")) {
            return "WebP image";
        }
        if (m.contains("heic") || m.contains("heif")) {
            return "HEIC image";
        }
        if (m.contains("gif")) {
            return "GIF image";
        }
        if (m.contains("bmp")) {
            return "BMP image";
        }
        return "Image";
    }

    public static String hex(int color) {
        return String.format(Locale.US, "#%06X", (0xFFFFFF & color));
    }
}
