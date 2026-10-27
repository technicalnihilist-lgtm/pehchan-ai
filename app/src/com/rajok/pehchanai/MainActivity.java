package com.rajok.pehchanai;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pehchan AI — identify anything in a photo, fully on device.
 * Single screen: pick a photo, tap Identify, read the result.
 */
public class MainActivity extends Activity {

    private static final int REQ_GALLERY = 101;
    private static final int REQ_CAMERA = 102;
    private static final int MAX_DIMENSION = 1600;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;

    private ScrollView scroll;
    private FrameLayout imageFrame;
    private ImageView previewImage;
    private View placeholder;
    private TextView btnGallery;
    private TextView btnCamera;
    private TextView btnIdentify;
    private TextView btnLens;
    private TextView btnWiki;
    private TextView btnShare;
    private View loadingRow;
    private TextView loadingText;
    private View resultsSection;
    private TextView topLabel;
    private TextView topSentence;
    private TextView topConfidence;
    private TextView topRankNote;
    private LinearLayout otherList;
    private LinearLayout detailsList;
    private LinearLayout colorsRow;

    private Classifier classifier;
    private Bitmap currentBitmap;
    private ImageInfo currentInfo;
    private String topRawLabel = "";
    private String topPrettyLabel = "";
    private int topPercent = 0;
    private File pendingCapture;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        worker = Executors.newSingleThreadExecutor();

        scroll = (ScrollView) findViewById(R.id.scroll);
        imageFrame = (FrameLayout) findViewById(R.id.imageFrame);
        previewImage = (ImageView) findViewById(R.id.previewImage);
        placeholder = findViewById(R.id.placeholder);
        btnGallery = (TextView) findViewById(R.id.btnGallery);
        btnCamera = (TextView) findViewById(R.id.btnCamera);
        btnIdentify = (TextView) findViewById(R.id.btnIdentify);
        btnLens = (TextView) findViewById(R.id.btnLens);
        btnWiki = (TextView) findViewById(R.id.btnWiki);
        btnShare = (TextView) findViewById(R.id.btnShare);
        loadingRow = findViewById(R.id.loadingRow);
        loadingText = (TextView) findViewById(R.id.loadingText);
        resultsSection = findViewById(R.id.resultsSection);
        topLabel = (TextView) findViewById(R.id.topLabel);
        topSentence = (TextView) findViewById(R.id.topSentence);
        topConfidence = (TextView) findViewById(R.id.topConfidence);
        topRankNote = (TextView) findViewById(R.id.topRankNote);
        otherList = (LinearLayout) findViewById(R.id.otherList);
        detailsList = (LinearLayout) findViewById(R.id.detailsList);
        colorsRow = (LinearLayout) findViewById(R.id.colorsRow);

        if (imageFrame != null) {
            imageFrame.setClipToOutline(true);
        }

        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFromGallery();
            }
        });
        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        btnIdentify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                identify();
            }
        });
        btnLens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLink("https://lens.google.com/");
            }
        });
        btnWiki.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = topRawLabel.length() > 0 ? Classifier.pretty(topRawLabel) : topPrettyLabel;
                if (query.length() == 0) {
                    toast("Identify a photo first");
                    return;
                }
                openLink("https://en.wikipedia.org/wiki/Special:Search?search=" + Uri.encode(query));
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareResult();
            }
        });

        setIdentifyEnabled(false);
        preloadClassifier();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (worker != null) {
            worker.shutdownNow();
        }
        if (classifier != null) {
            classifier.close();
            classifier = null;
        }
    }

    // ------------------------------------------------------------------ setup

    private void preloadClassifier() {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = new Classifier(getApplicationContext());
                } catch (final Throwable t) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("The AI engine could not start on this device.");
                        }
                    });
                }
            }
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) {
                loadFromUri((Uri) stream);
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            loadFromUri(intent.getData());
        }
    }

    // ------------------------------------------------------------- photo input

    private void pickFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            try {
                startActivityForResult(Intent.createChooser(fallback, "Select a photo"), REQ_GALLERY);
            } catch (ActivityNotFoundException e2) {
                toast("No gallery app found on this device.");
            }
        }
    }

    private void takePhoto() {
        try {
            File dir = getCacheDir();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            Uri output = FileShareProvider.uriFor(this, file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
            intent.setClipData(ClipData.newRawUri("photo", output));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pendingCapture = file;
            startActivityForResult(intent, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            toast("No camera app found on this device.");
        } catch (Throwable t) {
            toast("Could not open the camera.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                loadFromUri(data.getData());
            }
            return;
        }
        if (requestCode == REQ_CAMERA) {
            if (resultCode != RESULT_OK) {
                return;
            }
            if (pendingCapture != null && pendingCapture.exists() && pendingCapture.length() > 0) {
                loadFromFile(pendingCapture);
            } else if (data != null && data.getExtras() != null) {
                Object thumb = data.getExtras().get("data");
                if (thumb instanceof Bitmap) {
                    Bitmap bmp = (Bitmap) thumb;
                    applyImage(bmp, bmp.getWidth(), bmp.getHeight(), 0L, "image/jpeg");
                } else {
                    toast("The photo could not be saved. Please try again.");
                }
            } else {
                toast("The photo could not be saved. Please try again.");
            }
        }
    }

    private void loadFromUri(final Uri uri) {
        if (uri == null) {
            return;
        }
        setLoading(true, "Opening photo…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    InputStream is = getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(is, null, bounds);
                    closeQuietly(is);

                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        failLoad("That file is not a photo we can read.");
                        return;
                    }

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
                    is = getContentResolver().openInputStream(uri);
                    Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                    closeQuietly(is);

                    if (bmp == null) {
                        failLoad("That photo could not be opened. Try another one.");
                        return;
                    }
                    bmp = applyExif(bmp, uri, null);

                    long size = querySize(uri);
                    String mime = null;
                    try {
                        mime = getContentResolver().getType(uri);
                    } catch (Throwable ignored) {
                    }
                    final Bitmap ready = bmp;
                    final int w = bounds.outWidth;
                    final int h = bounds.outHeight;
                    final long bytes = size;
                    final String type = mime;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false, null);
                            applyImage(ready, w, h, bytes, type);
                        }
                    });
                } catch (Throwable t) {
                    failLoad("That photo could not be opened. Try another one.");
                }
            }
        });
    }

    private void loadFromFile(final File file) {
        if (file == null || !file.exists()) {
            return;
        }
        setLoading(true, "Opening photo…");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        failLoad("That photo could not be read.");
                        return;
                    }
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
                    Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                    if (bmp == null) {
                        failLoad("That photo could not be read.");
                        return;
                    }
                    bmp = applyExif(bmp, null, file.getAbsolutePath());
                    final Bitmap ready = bmp;
                    final int w = bounds.outWidth;
                    final int h = bounds.outHeight;
                    final long bytes = file.length();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false, null);
                            applyImage(ready, w, h, bytes, "image/jpeg");
                        }
                    });
                } catch (Throwable t) {
                    failLoad("That photo could not be read.");
                }
            }
        });
    }

    private void failLoad(final String message) {
        main.post(new Runnable() {
            @Override
            public void run() {
                setLoading(false, null);
                toast(message);
            }
        });
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        int maxSide = Math.max(width, height);
        while (maxSide / sample > MAX_DIMENSION) {
            sample *= 2;
        }
        return sample;
    }

    private static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Applies the EXIF rotation so portrait photos never show up sideways. */
    private Bitmap applyExif(Bitmap bitmap, Uri uri, String path) {
        try {
            ExifInterface exif = null;
            if (path != null) {
                exif = new ExifInterface(path);
            } else if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    exif = new ExifInterface(is);
                    closeQuietly(is);
                }
            }
            if (exif == null) {
                return bitmap;
            }
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.postScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.postScale(1f, -1f);
                    break;
                default:
                    return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Throwable ignored) {
            return bitmap;
        }
    }

    private long querySize(Uri uri) {
        try {
            android.database.Cursor cursor = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.SIZE}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                        if (index >= 0 && !cursor.isNull(index)) {
                            return cursor.getLong(index);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    // -------------------------------------------------------------- the result

    private void applyImage(Bitmap bitmap, int width, int height, long bytes, String mime) {
        Bitmap old = currentBitmap;
        currentBitmap = bitmap;
        currentInfo = ImageInfo.analyze(bitmap, width, height, bytes, mime);

        previewImage.setImageBitmap(bitmap);
        previewImage.setVisibility(View.VISIBLE);
        previewImage.setAlpha(0f);
        previewImage.animate().alpha(1f).setDuration(220).start();
        placeholder.setVisibility(View.GONE);

        resultsSection.setVisibility(View.GONE);
        setIdentifyEnabled(true);

        if (old != null && old != bitmap && !busy) {
            old.recycle();
        }
    }

    private void identify() {
        if (currentBitmap == null) {
            toast("Choose a photo first.");
            return;
        }
        if (busy) {
            return;
        }
        setLoading(true, "Looking at your photo…");
        final Bitmap source = currentBitmap;

        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (classifier == null) {
                        classifier = new Classifier(getApplicationContext());
                    }
                    final List<Classifier.Result> results = classifier.classify(source);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false, null);
                            showResults(results);
                        }
                    });
                } catch (final Throwable t) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false, null);
                            toast("Could not analyse this photo. Please try another one.");
                        }
                    });
                }
            }
        });
    }

    private void showResults(List<Classifier.Result> results) {
        if (results == null || results.isEmpty()) {
            toast("Nothing clear found. Try a closer, well-lit photo.");
            return;
        }

        Classifier.Result top = results.get(0);
        topRawLabel = top.rawLabel;
        topPrettyLabel = top.label;
        topPercent = Math.max(1, Math.round(top.score * 100f));

        topLabel.setText(capitalize(top.label));
        topConfidence.setText(topPercent + "% match");
        topSentence.setText("This looks like " + withArticle(top.label) + ".");
        if (topPercent >= 70) {
            topRankNote.setText("High confidence");
        } else if (topPercent >= 40) {
            topRankNote.setText("Fairly confident");
        } else {
            topRankNote.setText("Best guess — try a closer photo");
        }

        // ---- other possibilities
        otherList.removeAllViews();
        int added = 0;
        for (int i = 1; i < results.size(); i++) {
            Classifier.Result r = results.get(i);
            addPossibilityRow(capitalize(r.label), r.score, added == 0, 1f - (added * 0.18f));
            added++;
        }
        if (added == 0) {
            TextView none = new TextView(this);
            none.setText("No other close matches for this photo.");
            none.setTextColor(getResources().getColor(R.color.textDim));
            none.setTextSize(13f);
            otherList.addView(none);
        }

        // ---- image details
        detailsList.removeAllViews();
        ImageInfo info = currentInfo;
        if (info != null) {
            addDetailRow("Photo size", info.dimensionText());
            addDetailRow("File size", info.sizeText());
            addDetailRow("Lighting", info.brightness);
            addDetailRow("Type", info.formatText());

            colorsRow.removeAllViews();
            for (int i = 0; i < info.colors.size() && i < 5; i++) {
                colorsRow.addView(swatch(info.colors.get(i), i == info.colors.size() - 1));
            }
        }

        resultsSection.setVisibility(View.VISIBLE);
        resultsSection.setAlpha(0f);
        resultsSection.setTranslationY(dp(18));
        resultsSection.animate().alpha(1f).translationY(0f).setDuration(260).start();

        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.smoothScrollTo(0, Math.max(0, resultsSection.getTop() - dp(6)));
            }
        });
    }

    private void addPossibilityRow(String label, float score, boolean first, float fillAlpha) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = first ? 0 : dp(12);
        row.setLayoutParams(rowParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(getResources().getColor(R.color.text));
        name.setTextSize(14f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView pct = new TextView(this);
        pct.setText(Math.max(1, Math.round(score * 100f)) + "%");
        pct.setTextColor(getResources().getColor(R.color.textDim));
        pct.setTextSize(12.5f);

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(name, nameParams);
        header.addView(pct, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(header);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundResource(R.drawable.track_bg);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        barParams.topMargin = dp(6);
        bar.setLayoutParams(barParams);

        View fill = new View(this);
        fill.setBackgroundResource(R.drawable.bar_fill);
        fill.setAlpha(fillAlpha);

        View rest = new View(this);

        float weight = Math.max(score, 0.012f);
        bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1f - weight, 0.012f)));
        row.addView(bar);

        otherList.addView(row);
    }

    private void addDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(7);
        row.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(getResources().getColor(R.color.textDim));
        name.setTextSize(13f);

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(getResources().getColor(R.color.text));
        val.setTextSize(13f);
        val.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        val.setGravity(android.view.Gravity.END);

        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(val, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailsList.addView(row);
    }

    private View swatch(int color, boolean last) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!last) {
            params.rightMargin = dp(8);
        }
        column.setLayoutParams(params);

        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(dp(9));
        shape.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        dot.setBackground(shape);
        column.addView(dot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        TextView hex = new TextView(this);
        hex.setText(ImageInfo.hex(color));
        hex.setTextColor(getResources().getColor(R.color.textFaint));
        hex.setTextSize(9.5f);
        hex.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hexParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexParams.topMargin = dp(5);
        column.addView(hex, hexParams);

        return column;
    }

    // ------------------------------------------------------------- small bits

    private void setLoading(boolean loading, String message) {
        busy = loading;
        if (loading) {
            loadingRow.setVisibility(View.VISIBLE);
            if (message != null) {
                loadingText.setText(message);
            }
            btnIdentify.setAlpha(0.55f);
        } else {
            loadingRow.setVisibility(View.GONE);
            setIdentifyEnabled(currentBitmap != null);
        }
    }

    private void setIdentifyEnabled(boolean enabled) {
        btnIdentify.setAlpha(enabled ? 1f : 0.45f);
        btnIdentify.setClickable(enabled);
        btnIdentify.setFocusable(enabled);
    }

    private void shareResult() {
        if (currentBitmap == null) {
            toast("Choose a photo first.");
            return;
        }
        try {
            File out = new File(getCacheDir(), "pehchan_share.jpg");
            FileOutputStream fos = new FileOutputStream(out);
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
            fos.close();

            Uri uri = FileShareProvider.uriFor(this, out);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("photo", uri));
            intent.putExtra(Intent.EXTRA_TEXT, buildShareText());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, "Share with");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Throwable t) {
            toast("Could not share this photo.");
        }
    }

    private String buildShareText() {
        if (topPrettyLabel.length() == 0) {
            return "Identified with Pehchan AI.";
        }
        return "Pehchan AI says this is " + withArticle(topPrettyLabel)
                + " (" + topPercent + "% match).";
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            toast("No browser found on this device.");
        } catch (Throwable t) {
            toast("Could not open the link.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static String capitalize(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.US) + text.substring(1);
    }

    private static String withArticle(String text) {
        if (text == null || text.length() == 0) {
            return "something";
        }
        char first = Character.toLowerCase(text.charAt(0));
        boolean vowel = first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u';
        return (vowel ? "an " : "a ") + text;
    }
}
