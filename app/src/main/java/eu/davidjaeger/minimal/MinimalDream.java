package eu.davidjaeger.minimal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Screensaver in the launcher's style: photo with a slow zoom, large clock and date.
 * Photos: same gallery as the launcher (assets/wallpapers + files/wallpapers on the TV).
 */
public class MinimalDream extends DreamService {

    private static final long SLIDE_MS = 60_000;
    private static final long ZOOM_MS = 70_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView top, bottom;
    private boolean topVisible = true;
    private TextView clock, date;
    private LinearLayout clockBox;
    private List<String> photos = new ArrayList<>();
    private int index = 0;
    private int drift = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 10_000);
        }
    };

    private final Runnable slide = new Runnable() {
        @Override
        public void run() {
            nextPhoto();
            moveClock();
            handler.postDelayed(this, SLIDE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        bottom = makePhotoView();
        top = makePhotoView();
        root.addView(bottom, match());
        root.addView(top, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x99000000, 0x33000000, 0x00000000}));
        root.addView(shade, match());

        Typeface face = loadFont("fonts/display.ttf");

        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96);
        clock.setTypeface(face);
        clock.setLetterSpacing(-0.03f);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(10), 0, 0, 0x55000000);
        clockBox.addView(clock);

        date = new TextView(this);
        date.setTextColor(Color.WHITE);
        date.setAlpha(0.75f);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        date.setTypeface(face);
        date.setShadowLayer(dp(8), 0, 0, 0x55000000);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(6);
        dLp.leftMargin = dp(4);
        clockBox.addView(date, dLp);

        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        cLp.leftMargin = dp(64);
        cLp.bottomMargin = dp(56);
        root.addView(clockBox, cLp);

        setContentView(root);

        photos = photoList();
        index = 0;
        showPhoto(top, 0);
        updateClock();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
        handler.postDelayed(slide, SLIDE_MS);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- photos

    private ImageView makePhotoView() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    private void showPhoto(ImageView target, int i) {
        Bitmap bmp = photos.isEmpty() ? null : decode(photos.get(i % photos.size()));
        if (bmp != null) {
            target.setImageBitmap(bmp);
        } else {
            target.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF1E3347, 0xFF0E1A26, 0xFF06090D}));
        }
        kenBurns(target, i);
    }

    /** Slow zoom, alternating direction so it never feels repetitive. */
    private void kenBurns(ImageView v, int i) {
        v.animate().cancel();
        boolean zoomIn = i % 2 == 0;
        float from = zoomIn ? 1.0f : 1.10f, to = zoomIn ? 1.10f : 1.0f;
        float shift = dp(24) * (i % 3 == 0 ? 1 : -1);
        v.setScaleX(from);
        v.setScaleY(from);
        v.setTranslationX(zoomIn ? 0 : shift);
        v.animate().scaleX(to).scaleY(to).translationX(zoomIn ? shift : 0)
                .setDuration(ZOOM_MS).setInterpolator(new LinearInterpolator()).start();
    }

    private void nextPhoto() {
        if (photos.size() < 2) {
            // only one photo: restart the slow zoom in the other direction
            index++;
            kenBurns(topVisible ? top : bottom, index);
            return;
        }
        index = (index + 1) % photos.size();
        if (topVisible) {
            showPhoto(bottom, index);
            top.animate().alpha(0f).setDuration(2500).start();
        } else {
            showPhoto(top, index);
            top.setAlpha(0f);
            top.animate().alpha(1f).setDuration(2500).start();
        }
        topVisible = !topVisible;
    }

    private List<String> photoList() {
        List<String> out = new ArrayList<>();
        try {
            String[] names = getAssets().list("wallpapers");
            if (names != null) {
                List<String> sorted = new ArrayList<>();
                for (String n : names) if (isImage(n)) sorted.add(n);
                Collections.sort(sorted);
                for (String n : sorted) out.add("asset:wallpapers/" + n);
            }
        } catch (Throwable ignored) {
        }
        File dir = getExternalFilesDir("wallpapers");
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) {
                List<String> sorted = new ArrayList<>();
                for (File f : files) if (isImage(f.getName())) sorted.add(f.getAbsolutePath());
                Collections.sort(sorted);
                for (String f : sorted) out.add("file:" + f);
            }
        }
        return out;
    }

    private static boolean isImage(String n) {
        String l = n.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
    }

    private Bitmap decode(String key) {
        try {
            if (key.startsWith("asset:")) {
                InputStream in = getAssets().open(key.substring(6));
                try {
                    return BitmapFactory.decodeStream(in);
                } finally {
                    in.close();
                }
            }
            String path = key.substring(5);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- clock

    private void updateClock() {
        Date now = new Date();
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
    }

    /** Moves the clock a little every minute so nothing stays in one place for hours. */
    private void moveClock() {
        drift = (drift + 1) % 4;
        float dx = dp(10) * (drift % 2 == 0 ? 0 : 1);
        float dy = -dp(8) * (drift / 2);
        clockBox.animate().translationX(dx).translationY(dy).setDuration(1500).start();
    }

    // ---------------------------------------------------------------- helpers

    private Typeface loadFont(String asset) {
        try {
            return Typeface.createFromAsset(getAssets(), asset);
        } catch (Throwable t) {
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
