package eu.davidjaeger.minimal;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

/**
 * NTS Radio player in the launcher's style.
 * Live channels use the stream URLs NTS publishes for custom players.
 */
public class NtsActivity extends Activity {

    // {title, stream url, live channel name in the NTS API or "" for mixtapes}
    private static final String[][] STATIONS = {
            {"NTS 1", "https://stream-relay-geo.ntslive.net/stream?client=direct", "1"},
            {"NTS 2", "https://stream-relay-geo.ntslive.net/stream2?client=direct", "2"},
            {"Slow Focus", "https://stream-mixtape-geo.ntslive.net/mixtape?client=direct", ""},
            {"Low Key", "https://stream-mixtape-geo.ntslive.net/mixtape2?client=direct", ""},
            {"Poolside", "https://stream-mixtape-geo.ntslive.net/mixtape4?client=direct", ""},
            {"4 to the Floor", "https://stream-mixtape-geo.ntslive.net/mixtape5?client=direct", ""},
            {"Memory Lane", "https://stream-mixtape-geo.ntslive.net/mixtape6?client=direct", ""},
            {"Island Time", "https://stream-mixtape-geo.ntslive.net/mixtape21?client=direct", ""},
    };
    private static final String LIVE_API = "https://www.nts.live/api/v2/live";
    private static final float DIM_ALPHA = 0.55f;

    // One player for the whole app, so music keeps going while the screensaver runs.
    private static MediaPlayer player;
    private static int playingIndex = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Typeface face, textFace;
    private ImageView background;
    private TextView clock;
    private LinearLayout list;
    private final TextView[] subtitles = new TextView[STATIONS.length];
    private final View[] dots = new View[STATIONS.length];
    private final String[] showTitle = new String[STATIONS.length];
    private final String[] showImage = new String[STATIONS.length];
    private String currentImage = null;
    private int focusedIndex = 0;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            loadLiveInfo();
            handler.postDelayed(this, 60_000);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(DateFormat.format(
                    DateFormat.is24HourFormat(NtsActivity.this) ? "HH:mm" : "h:mm", new Date()));
            handler.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        face = font("fonts/display.ttf");
        textFace = font("fonts/text.ttf");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        background.setColorFilter(new ColorMatrixColorFilter(grey));
        background.setAlpha(0.6f);
        root.addView(background, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE6000000, 0x80000000, 0x26000000}));
        root.addView(shade, match());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(120));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(100), dp(40), dp(140));
        scroll.addView(list);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        sLp.leftMargin = dp(48);
        root.addView(scroll, sLp);

        TextView header = new TextView(this);
        header.setText("NTS Radio");
        header.setTextColor(Color.WHITE);
        header.setAlpha(0.7f);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(textFace);
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        hLp.leftMargin = dp(64);
        hLp.topMargin = dp(48);
        root.addView(header, hLp);

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        clock.setTypeface(face);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        cLp.topMargin = dp(40);
        cLp.rightMargin = dp(56);
        root.addView(clock, cLp);

        for (int i = 0; i < STATIONS.length; i++) {
            if (i == 2) list.addView(sectionLabel("Infinite Mixtapes"));
            list.addView(makeRow(i));
        }

        setContentView(root);
        updateDots();
        final View first = list.getChildAt(playingIndex >= 2 ? playingIndex + 1 : Math.max(playingIndex, 0));
        if (first != null) first.post(new Runnable() {
            @Override
            public void run() {
                first.requestFocus();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
        handler.removeCallbacks(tick);
    }

    // ---------------------------------------------------------------- UI

    private View sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setAlpha(0.5f);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(textFace);
        tv.setPadding(dp(14), dp(28), 0, dp(6));
        return tv;
    }

    private View makeRow(final int index) {
        boolean live = !STATIONS[index][2].isEmpty();
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(14), dp(6), dp(26), dp(6));

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.WHITE);
        dot.setBackground(d);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(16);
        row.addView(dot, dotLp);
        dots[index] = dot;

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(STATIONS[index][0]);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, live ? 40 : 28);
        title.setTypeface(face);
        title.setLetterSpacing(-0.02f);
        title.setSingleLine(true);
        texts.addView(title);
        if (live) {
            TextView sub = new TextView(this);
            sub.setTextColor(Color.WHITE);
            sub.setAlpha(0.75f);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            sub.setTypeface(textFace);
            sub.setSingleLine(true);
            sub.setMaxWidth(dp(560));
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(sub);
            subtitles[index] = sub;
        }
        row.addView(texts);

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setCornerRadius(dp(14));
        focusBg.setColor(0x26FFFFFF);
        row.setAlpha(DIM_ALPHA);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackground(hasFocus ? focusBg : null);
                row.animate().alpha(hasFocus ? 1f : DIM_ALPHA).setDuration(120).start();
                if (hasFocus) {
                    focusedIndex = index;
                    updateBackground();
                }
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle(index);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private void updateDots() {
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] != null) dots[i].setVisibility(i == playingIndex ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** Background shows the artwork of the focused live channel, else of the playing one. */
    private void updateBackground() {
        String url = showImage[focusedIndex];
        if (url == null && playingIndex >= 0) url = showImage[playingIndex];
        if (url == null) url = showImage[0];
        if (url == null || url.equals(currentImage)) return;
        currentImage = url;
        final String target = url;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = downloadBitmap(target);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (bmp != null && target.equals(currentImage)) {
                            background.setAlpha(0f);
                            background.setImageBitmap(bmp);
                            background.animate().alpha(0.6f).setDuration(600).start();
                        }
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- playback

    private void toggle(int index) {
        if (index == playingIndex) {
            stop();
        } else {
            play(index);
        }
        updateDots();
    }

    private void play(int index) {
        stop();
        AudioManager am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(STATIONS[index][1]);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stop();
                    updateDots();
                    return true;
                }
            });
            player.prepareAsync();
            playingIndex = index;
        } catch (Throwable t) {
            stop();
        }
    }

    private static void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            player.release();
            player = null;
        }
        playingIndex = -1;
    }

    // Another app (YouTube, Netflix...) takes over the sound -> NTS stops.
    private static final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stop();
                }
            };

    // ---------------------------------------------------------------- now playing info

    private void loadLiveInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = new String(download(LIVE_API), "UTF-8");
                    JSONArray results = new JSONObject(json).getJSONArray("results");
                    for (int r = 0; r < results.length(); r++) {
                        JSONObject ch = results.getJSONObject(r);
                        String name = ch.optString("channel_name");
                        JSONObject now = ch.optJSONObject("now");
                        if (now == null) continue;
                        String title = now.optString("broadcast_title", "");
                        String location = "", image = null;
                        JSONObject embeds = now.optJSONObject("embeds");
                        JSONObject details = embeds == null ? null : embeds.optJSONObject("details");
                        if (details != null) {
                            location = details.optString("location_long", "");
                            JSONObject media = details.optJSONObject("media");
                            if (media != null) {
                                image = media.optString("background_large", null);
                                if (image == null || image.isEmpty()) image = media.optString("picture_large", null);
                            }
                        }
                        for (int i = 0; i < STATIONS.length; i++) {
                            if (STATIONS[i][2].equals(name)) {
                                showTitle[i] = location.isEmpty() ? title : title + "  ·  " + location;
                                showImage[i] = image;
                            }
                        }
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < STATIONS.length; i++) {
                                if (subtitles[i] != null && showTitle[i] != null) {
                                    subtitles[i].setText(titleCase(showTitle[i]));
                                }
                            }
                            currentImage = null;
                            updateBackground();
                        }
                    });
                } catch (Throwable ignored) {
                    // offline or API changed: player still works without show info
                }
            }
        }).start();
    }

    private static String titleCase(String s) {
        // NTS titles are often ALL CAPS; soften them for the calm look
        if (!s.equals(s.toUpperCase())) return s;
        StringBuilder b = new StringBuilder();
        boolean start = true;
        for (char c : s.toLowerCase().toCharArray()) {
            b.append(start ? Character.toUpperCase(c) : c);
            start = Character.isWhitespace(c) || c == '(' || c == '/';
        }
        return b.toString();
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "minimal-launcher");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static Bitmap downloadBitmap(String url) {
        try {
            byte[] data = download(url);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private Typeface font(String asset) {
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
