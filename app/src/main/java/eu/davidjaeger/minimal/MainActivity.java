package eu.davidjaeger.minimal;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal TV launcher.
 *
 * Home screen apps: edit FAVORITES below (display name + package name).
 * Wallpaper: put a JPG at
 *   /sdcard/Android/data/eu.davidjaeger.minimal/files/wallpaper.jpg
 * (e.g. via: adb push foto.jpg /sdcard/Android/data/eu.davidjaeger.minimal/files/wallpaper.jpg)
 */
public class MainActivity extends Activity {

    // Home screen, in this order. {display name, package name, icon style}
    // icon style: "mono" = app icon in grey/white, "mask" = only the bright
    // shape of the app icon on a transparent background.
    private static final String[][] FAVORITES = {
            {"YouTube", "com.google.android.youtube.tv", "white"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom", "mono"},
            {"Netflix", "com.netflix.ninja", "mask"},
            {"MUBI", "com.mubi", "mono"},
            {"Disney+", "com.disney.disneyplus", "white"},
            {"Apple TV", "com.apple.atve.androidtv.appletv", "mono"},
            {"Spotify", "com.spotify.tv.android", "mono"},
    };

    // "Source" entry: first package that can be launched wins.
    private static final String[] SOURCE_PACKAGES = {
            "com.tcl.tvinput", "com.tcl.tv", "com.android.tv"
    };

    private static final int TEXT_COLOR = Color.WHITE;
    private static final float DIM_ALPHA = 0.55f;

    private ImageView wallpaper;
    private LinearLayout list;
    private ScrollView scroll;
    private TextView clock;
    private LinearLayout corner;
    private boolean showingAll = false;
    private String currentWallpaper = null;
    private Typeface displayFace;
    private Typeface textFace;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        displayFace = loadFont("fonts/display.ttf");
        textFace = loadFont("fonts/text.ttf");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Soft shade on the left so the text stays readable on any photo
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xB3000000, 0x40000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x80000000, 0x00000000}));
        FrameLayout.LayoutParams bsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160), Gravity.BOTTOM);
        root.addView(bottomShade, bsLp);

        // App list (left)
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(72));
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(24), 0, dp(24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(40), dp(40), dp(80));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams scLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START);
        scLp.leftMargin = dp(48);
        scLp.topMargin = dp(40);
        scLp.bottomMargin = dp(24);
        root.addView(scroll, scLp);

        // Clock (top right)
        clock = new TextView(this);
        clock.setTextColor(TEXT_COLOR);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        clock.setTypeface(displayFace);
        clock.setShadowLayer(dp(6), 0, 0, 0x66000000);
        FrameLayout.LayoutParams clLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clLp.topMargin = dp(40);
        clLp.rightMargin = dp(56);
        root.addView(clock, clLp);

        // All apps / Settings (bottom right)
        corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        corner.setGravity(Gravity.END);
        corner.addView(makeCornerItem("All apps", makeGridIcon(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showingAll = true;
                render();
            }
        }));
        corner.addView(makeCornerItem("Wallpaper", makeFrameIcon(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextWallpaper();
            }
        }));
        corner.addView(makeCornerItem("Settings", makeGearIcon(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        }));
        FrameLayout.LayoutParams coLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        coLp.bottomMargin = dp(36);
        coLp.rightMargin = dp(44);
        root.addView(corner, coLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWallpaper();
        render();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while we are already open -> back to the home list
        if (showingAll) {
            showingAll = false;
            render();
        }
    }

    @Override
    public void onBackPressed() {
        if (showingAll) {
            showingAll = false;
            render();
        }
        // On the home screen, back does nothing (we are the launcher).
    }

    // ---------------------------------------------------------------- rendering

    private void render() {
        list.removeAllViews();
        PackageManager pm = getPackageManager();

        if (!showingAll) {
            corner.setVisibility(View.VISIBLE);
            Map<String, String> installed = null;
            for (String[] fav : FAVORITES) {
                String found = fav[1];
                if (launchIntentFor(found) == null) {
                    // package name differs on this TV? -> find the app by its name
                    if (installed == null) installed = allApps(false);
                    found = null;
                    for (Map.Entry<String, String> e : installed.entrySet()) {
                        if (e.getValue().trim().equalsIgnoreCase(fav[0])
                                || e.getValue().toLowerCase().startsWith(fav[0].toLowerCase())) {
                            found = e.getKey();
                            break;
                        }
                    }
                    if (found == null) continue; // not installed -> skip
                }
                final String pkg = found;
                Drawable own = customIcon(pkg);
                ColorMatrixColorFilter filter = own != null ? null
                        : "mask".equals(fav[2]) ? mask()
                        : "white".equals(fav[2]) ? whiteOnly()
                        : monochrome();
                list.addView(makeRow(fav[0], own != null ? own : loadIcon(pkg), filter, 40, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launch(pkg);
                    }
                }));
            }
            Drawable srcIcon = customIcon("source");
            list.addView(makeRow("Source", srcIcon != null ? srcIcon : new SourceIcon(), null, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSource();
                }
            }));
        } else {
            corner.setVisibility(View.GONE);
            for (Map.Entry<String, String> app : allApps(true).entrySet()) {
                final String pkg = app.getKey();
                list.addView(makeRow(app.getValue(), loadIcon(pkg), monochrome(), 30, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launch(pkg);
                    }
                }));
            }
        }

        scroll.scrollTo(0, 0);
        if (list.getChildCount() > 0) {
            final View first = list.getChildAt(0);
            first.post(new Runnable() {
                @Override
                public void run() {
                    first.requestFocus();
                }
            });
        }
    }

    private View makeRow(String title, Drawable icon, ColorFilter filter, int textSp,
                         View.OnClickListener onClick) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(14), dp(6), dp(26), dp(6));
        row.setOnClickListener(onClick);

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setCornerRadius(dp(14));
        focusBg.setColor(0x26FFFFFF);

        int iconSize = (int) (dp(textSp) * 0.62f);
        ImageView iv = new ImageView(this);
        if (icon != null) {
            iv.setImageDrawable(icon);
            if (filter != null) iv.setColorFilter(filter);
        }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ivLp.rightMargin = dp(14);
        row.addView(iv, ivLp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(TEXT_COLOR);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        tv.setTypeface(displayFace);
        tv.setLetterSpacing(-0.02f);
        tv.setShadowLayer(dp(8), 0, 0, 0x55000000);
        tv.setSingleLine(true);
        row.addView(tv);

        row.setAlpha(DIM_ALPHA);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackground(hasFocus ? focusBg : null);
                row.animate().alpha(hasFocus ? 1f : DIM_ALPHA).setDuration(120).start();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private View makeCornerItem(String title, View icon, View.OnClickListener onClick) {
        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(dp(12), dp(6), dp(12), dp(6));
        item.setOnClickListener(onClick);

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setCornerRadius(dp(12));
        focusBg.setColor(0x26FFFFFF);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(TEXT_COLOR);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(textFace);
        item.addView(tv);

        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        icLp.leftMargin = dp(10);
        item.addView(icon, icLp);

        item.setAlpha(0.8f);
        item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                item.setBackground(hasFocus ? focusBg : null);
                item.setAlpha(hasFocus ? 1f : 0.8f);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        item.setLayoutParams(lp);
        return item;
    }

    /** 2x2 grid of small rounded squares, drawn in code. */
    private View makeGridIcon() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 2; r++) {
            LinearLayout rowL = new LinearLayout(this);
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                View cell = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(2));
                g.setStroke(Math.max(1, dp(1)), TEXT_COLOR);
                cell.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                rowL.addView(cell, lp);
            }
            grid.addView(rowL, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return grid;
    }

    /** Small rounded rectangle as a "wallpaper" mark. */
    private View makeFrameIcon() {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(3));
        g.setStroke(Math.max(2, dp(2)), TEXT_COLOR);
        v.setBackground(g);
        return v;
    }

    /** Simple ring as a quiet "settings" mark. */
    private View makeGearIcon() {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Math.max(2, dp(2)), TEXT_COLOR);
        v.setBackground(g);
        return v;
    }

    private void updateClock() {
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        clock.setText(DateFormat.format(pattern, new Date()));
    }

    // ---------------------------------------------------------------- wallpaper

    /** All wallpapers: built-in (assets/wallpapers) + own files (files/wallpapers on the TV). */
    private List<String> wallpaperList() {
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

    private void nextWallpaper() {
        List<String> all = wallpaperList();
        if (all.isEmpty()) return;
        int idx = getSharedPreferences("minimal", MODE_PRIVATE).getInt("wallpaper", 0);
        idx = (idx + 1) % all.size();
        getSharedPreferences("minimal", MODE_PRIVATE).edit().putInt("wallpaper", idx).apply();
        currentWallpaper = null;
        loadWallpaper();
    }

    private void loadWallpaper() {
        List<String> all = wallpaperList();
        if (all.isEmpty()) {
            wallpaper.setImageDrawable(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF1E3347, 0xFF0E1A26, 0xFF06090D}));
            currentWallpaper = null;
            return;
        }
        int idx = getSharedPreferences("minimal", MODE_PRIVATE).getInt("wallpaper", 0);
        if (idx < 0 || idx >= all.size()) idx = 0;
        String key = all.get(idx);
        if (key.equals(currentWallpaper)) return;
        Bitmap bmp = decodeWallpaper(key);
        if (bmp != null) {
            wallpaper.setImageBitmap(bmp);
            currentWallpaper = key;
        }
    }

    private Bitmap decodeWallpaper(String key) {
        try {
            if (key.startsWith("asset:")) {
                java.io.InputStream in = getAssets().open(key.substring(6));
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

    /** Own icon: assets/icons/<package>.png, or files/icons/<package>.png on the TV. */
    private Drawable customIcon(String pkg) {
        File dir = getExternalFilesDir("icons");
        if (dir != null) {
            File f = new File(dir, pkg + ".png");
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b != null) return new android.graphics.drawable.BitmapDrawable(getResources(), b);
            }
        }
        try {
            java.io.InputStream in = getAssets().open("icons/" + pkg + ".png");
            try {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null) return new android.graphics.drawable.BitmapDrawable(getResources(), b);
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- apps

    private Intent launchIntentFor(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Throwable ignored) {
        }
        if (i == null) {
            try {
                i = pm.getLaunchIntentForPackage(pkg);
            } catch (Throwable ignored) {
            }
        }
        return i;
    }

    private void launch(String pkg) {
        Intent i = launchIntentFor(pkg);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            safeStart(i);
        }
    }

    private void openSource() {
        for (String pkg : SOURCE_PACKAGES) {
            Intent i = launchIntentFor(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (safeStart(i)) return;
            }
        }
        safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Drawable loadIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Sorted map package -> label of every launchable app (TV and phone apps). */
    // Hidden from "All apps": system parts, TCL tools, regional bloat, launchers.
    private static final String[] HIDDEN_PREFIXES = {
            "com.tcl.", "com.android.", "com.google.android.", "uk.co.freeview.",
            "au.com.stan", "tv.wuaki", "com.tvos", "android.", "com.spocky.",
            "app.olauncher", "com.slate.launcher"
    };
    // ...except these, which stay visible.
    private static final String[] ALWAYS_SHOW = {
            "com.google.android.youtube.tv", "com.android.vending"
    };

    private static boolean isHidden(String pkg) {
        for (String keep : ALWAYS_SHOW) if (pkg.equals(keep)) return false;
        for (String pre : HIDDEN_PREFIXES) if (pkg.startsWith(pre)) return true;
        return false;
    }

    private Map<String, String> allApps(boolean filtered) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            try {
                found.addAll(pm.queryIntentActivities(q, 0));
            } catch (Throwable ignored) {
            }
        }
        final Map<String, String> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
            if (filtered && isHidden(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            byPkg.put(pkg, label == null ? pkg : label.toString());
        }
        List<String> keys = new ArrayList<>(byPkg.keySet());
        Collections.sort(keys, (a, b) -> byPkg.get(a).compareToIgnoreCase(byPkg.get(b)));
        Map<String, String> sorted = new LinkedHashMap<>();
        for (String k : keys) sorted.put(k, byPkg.get(k));
        return sorted;
    }

    // ---------------------------------------------------------------- helpers

    /** Turns colourful app icons into soft white/grey marks. */
    private static ColorMatrixColorFilter monochrome() {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        ColorMatrix lift = new ColorMatrix(new float[]{
                1.2f, 0, 0, 0, 30,
                0, 1.2f, 0, 0, 30,
                0, 0, 1.2f, 0, 30,
                0, 0, 0, 0.9f, 0
        });
        cm.postConcat(lift);
        return new ColorMatrixColorFilter(cm);
    }

    /** Keeps only the bright parts of an icon, as white on transparent. */
    private static ColorMatrixColorFilter mask() {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0.9f, 1.8f, 0.35f, 0, -40
        }));
    }

    /** Keeps only near-white parts of an icon (e.g. a white glyph on a coloured tile). */
    private static ColorMatrixColorFilter whiteOnly() {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                1.2f, 2.36f, 0.44f, 0, -800
        }));
    }

    /** Minimal "input source" mark: a screen outline with an arrow pointing in. */
    private static class SourceIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SourceIcon() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            paint.setStrokeWidth(Math.max(2f, w * 0.08f));
            RectF screen = new RectF(x + w * 0.10f, y + h * 0.16f, x + w * 0.90f, y + h * 0.72f);
            c.drawRoundRect(screen, w * 0.08f, w * 0.08f, paint);
            c.drawLine(x + w * 0.36f, y + h * 0.88f, x + w * 0.64f, y + h * 0.88f, paint);
            float ay = y + h * 0.44f;
            c.drawLine(x + w * 0.30f, ay, x + w * 0.62f, ay, paint);
            c.drawLine(x + w * 0.50f, ay - h * 0.11f, x + w * 0.62f, ay, paint);
            c.drawLine(x + w * 0.50f, ay + h * 0.11f, x + w * 0.62f, ay, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private Typeface loadFont(String asset) {
        try {
            return Typeface.createFromAsset(getAssets(), asset);
        } catch (Throwable t) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
