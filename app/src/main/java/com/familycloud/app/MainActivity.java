package com.familycloud.app;

import android.view.View;

import android.graphics.RectF;

import android.graphics.Paint;

import android.graphics.Canvas;

import android.content.Intent;

import android.view.Window;

import android.os.Build;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String KEY_COOKIE = "cookie";
    public static final String ONLINE_URL = "http://100.109.57.8:3000";

    public static final String PREFS = "sri_ladli_family_cloud";

    public static final String KEY_BASE_URL = "baseUrl";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_EMAIL = "email";

    public static final String KEY_LOCAL_URL = "localUrl";
    public static final String KEY_NATIVE_URL = "nativeUrl";

    public static final String DEFAULT_NATIVE_URL = "http://100.109.57.8:3000";

    private LinearLayout root;
    private SharedPreferences prefs;

    private String baseUrl = "";
    private String token = "";
    private String email = "";

    private String localUrl = "";
    private String nativeUrl = DEFAULT_NATIVE_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindowBars();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        baseUrl = prefs.getString(KEY_BASE_URL, "");
        token = prefs.getString(KEY_TOKEN, "");
        email = prefs.getString(KEY_EMAIL, "");

        localUrl = prefs.getString(KEY_LOCAL_URL, "");
        nativeUrl = prefs.getString(KEY_NATIVE_URL, DEFAULT_NATIVE_URL);

        if (nativeUrl == null || nativeUrl.trim().isEmpty()) {
            nativeUrl = DEFAULT_NATIVE_URL;
        }

        if (token.length() > 8 && baseUrl.length() > 8) {
            showDashboard();
        } else {
            showFirstScreen();
        }
    }

    
private void setupWindowBars() {
    try {
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
    } catch (Exception ignored) {}
}

private void showFirstScreen() {
        base();

        LinearLayout card = cardBox();
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        logo.setAdjustViewBounds(true);
        card.addView(logo, new LinearLayout.LayoutParams(dp(122), dp(122)));

        TextView title = text("SRI LADLI", 32, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = text("Choose connection type", 16, Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);

        TextView info = text("Local starts empty. Native server is prefilled. Use Edit URLs to change both.", 14, Color.rgb(170, 170, 170));
        info.setGravity(Gravity.CENTER);
        card.addView(info);

        Button local = primaryButton("Local Server");
        local.setText("Local Server\n" + (localUrl.trim().isEmpty() ? "URL empty, edit or enter during login" : localUrl));
        local.setOnClickListener(v -> showLogin("Local Server", localUrl, true));

        Button nativeServer = primaryButton("Native Server");
        nativeServer.setText("Native Server\n" + nativeUrl);
        nativeServer.setOnClickListener(v -> showLogin("Native Server", nativeUrl, false));

        Button edit = ghostButton("Edit URLs");
        edit.setText("Edit URLs\nChange Local and Native server addresses");
        edit.setOnClickListener(v -> showEditUrls());

        card.addView(local, fullParams());
        card.addView(nativeServer, fullParams());
        card.addView(edit, fullParams());

        root.addView(card, fullParams());
    }

    private void showEditUrls() {
        base();

        LinearLayout card = cardBox();

        TextView title = text("Edit Server URLs", 26, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView note = text("Local URL can stay empty. Native URL is prefilled for now.", 14, Color.LTGRAY);
        note.setGravity(Gravity.CENTER);
        card.addView(note);

        EditText localInput = input("Local URL, example http://192.168.1.5:3000", localUrl);
        EditText nativeInput = input("Native URL", nativeUrl == null || nativeUrl.trim().isEmpty() ? DEFAULT_NATIVE_URL : nativeUrl);

        Button save = primaryButton("Save URLs");
        save.setOnClickListener(v -> {
            localUrl = cleanUrl(localInput.getText().toString());
            nativeUrl = cleanUrl(nativeInput.getText().toString());

            if (nativeUrl.trim().isEmpty()) {
                nativeUrl = DEFAULT_NATIVE_URL;
            }

            prefs.edit()
                    .putString(KEY_LOCAL_URL, localUrl)
                    .putString(KEY_NATIVE_URL, nativeUrl)
                    .apply();

            toast("URLs saved");
            showFirstScreen();
        });

        Button resetNative = ghostButton("Reset Native URL");
        resetNative.setOnClickListener(v -> {
            nativeInput.setText(DEFAULT_NATIVE_URL);
            toast("Native URL reset");
        });

        Button back = ghostButton("Back");
        back.setOnClickListener(v -> showFirstScreen());

        card.addView(localInput, fullParams());
        card.addView(nativeInput, fullParams());
        card.addView(save, fullParams());
        card.addView(resetNative, fullParams());
        card.addView(back, fullParams());

        root.addView(card, fullParams());
    }

    private void showLogin(String mode, String suggestedUrl, boolean allowUrlEditHere) {
    base();

    LinearLayout card = cardBox();

    TextView title = text(mode, 26, Color.rgb(255, 196, 0));
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setGravity(Gravity.CENTER);
    card.addView(title);

    boolean isLocal = mode.toLowerCase(Locale.ROOT).contains("local");

    TextView sub = text(
            isLocal
                    ? "Enter your local server URL, then login."
                    : "Native server selected. Enter user ID and password.",
            14,
            Color.LTGRAY
    );
    sub.setGravity(Gravity.CENTER);
    card.addView(sub);

    final EditText server;

    if (allowUrlEditHere) {
        server = input("Local URL, example http://192.168.1.5:3000", suggestedUrl == null ? "" : suggestedUrl);
        card.addView(server, fullParams());
    } else {
        server = null;
        String shownUrl = cleanUrl(suggestedUrl == null || suggestedUrl.trim().isEmpty() ? DEFAULT_NATIVE_URL : suggestedUrl);
        nativeUrl = shownUrl;

        TextView nativeText = text("Server: " + shownUrl, 13, Color.rgb(170, 170, 170));
        nativeText.setGravity(Gravity.CENTER);
        card.addView(nativeText);
    }

    EditText user = input("User ID / Email", email);

    EditText pass = input("Password", "");
    pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    Button login = primaryButton("Login");
    login.setOnClickListener(v -> {
        String url;

        if (allowUrlEditHere) {
            url = cleanUrl(server.getText().toString());
        } else {
            url = cleanUrl(nativeUrl == null || nativeUrl.trim().isEmpty() ? DEFAULT_NATIVE_URL : nativeUrl);
        }

        String userId = user.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = pass.getText().toString();

        if (url.length() < 8) {
            toast("Enter server URL");
            return;
        }

        if (userId.length() < 2) {
            toast("Enter user ID or email");
            return;
        }

        if (password.length() < 1) {
            toast("Enter password");
            return;
        }

        baseUrl = url;

        if (allowUrlEditHere) {
            localUrl = url;
            prefs.edit().putString(KEY_LOCAL_URL, localUrl).apply();
        } else {
            nativeUrl = url;
            prefs.edit().putString(KEY_NATIVE_URL, nativeUrl).apply();
        }

        doLogin(userId, password);
    });

    Button editUrls = ghostButton("Edit URLs");
    editUrls.setOnClickListener(v -> showEditUrls());

    Button back = ghostButton("Back");
    back.setOnClickListener(v -> showFirstScreen());

    card.addView(user, fullParams());
    card.addView(pass, fullParams());
    card.addView(login, fullParams());
    card.addView(editUrls, fullParams());
    card.addView(back, fullParams());

    root.addView(card, fullParams());
}



    private void doLogin(String userId, String password) {
        showLoading("Logging in...");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", userId);
                body.put("username", userId);
                body.put("userId", userId);
                body.put("password", password);

                JSONObject r = postJson("/api/native/login", body, false);

                if (!r.optBoolean("ok")) {
                    throw new Exception(r.optString("error", "Login failed"));
                }

                token = r.optString("token", "");
                email = r.optString("email", userId);

                if (token.length() < 8) {
                    throw new Exception("Server returned empty token");
                }

                prefs.edit()
                        .putString(KEY_BASE_URL, baseUrl)
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_EMAIL, email)
                        .apply();

                runOnUiThread(this::showDashboard);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("Login failed: " + e.getMessage());
                    showLogin("Retry Login", baseUrl, true);
                });
            }
        }).start();
    }

    private void showDashboard() {
    base();

    TextView heading = text("SRI LADLI", 28, Color.rgb(255, 196, 0));
    heading.setTypeface(Typeface.DEFAULT_BOLD);
    heading.setGravity(Gravity.CENTER);
    root.addView(heading, fullParams());

    TextView logged = text("Logged in as: " + (email.length() > 0 ? email : "User"), 15, Color.LTGRAY);
    logged.setGravity(Gravity.CENTER);
    root.addView(logged, fullParams());

    LinearLayout storageCard = cardBox();
    storageCard.setGravity(Gravity.CENTER_HORIZONTAL);

    TextView loading = text("Loading storage details...", 15, Color.LTGRAY);
    loading.setGravity(Gravity.CENTER);

    CircleProgressView circle = new CircleProgressView(this);
    circle.setPercent(0);

    storageCard.addView(circle, new LinearLayout.LayoutParams(dp(185), dp(185)));
    storageCard.addView(loading, fullParams());
    root.addView(storageCard, fullParams());

    LinearLayout statsCard = cardBox();
    TextView filesText = text("Total files: --", 16, Color.WHITE);
    TextView photosText = text("Total photos: --", 16, Color.WHITE);
    TextView videosText = text("Total videos: --", 16, Color.WHITE);
    TextView tempText = text("Drive temperature: --°C", 16, Color.rgb(255, 196, 0));

    statsCard.addView(filesText, fullParams());
    statsCard.addView(photosText, fullParams());
    statsCard.addView(videosText, fullParams());
    statsCard.addView(tempText, fullParams());
    root.addView(statsCard, fullParams());

    TextView quickTitle = text("Quick Actions", 20, Color.rgb(255, 196, 0));
    quickTitle.setTypeface(Typeface.DEFAULT_BOLD);
    root.addView(quickTitle, fullParams());

    LinearLayout quick = cardBox();

    Button gallery = primaryButton("Gallery");
    gallery.setOnClickListener(v -> openPage(GalleryActivity.class));

    Button upload = primaryButton("Upload Files");
    upload.setOnClickListener(v -> openPage(UploadActivity.class));

    Button account = primaryButton("Account");
    account.setOnClickListener(v -> openPage(AccountActivity.class));

    Button backup = primaryButton("Background Backup");
    backup.setOnClickListener(v -> openPage(BackupActivity.class));

    Button admin = ghostButton("Admin");
    admin.setOnClickListener(v -> openPage(AdminActivity.class));

    Button logout = ghostButton("Logout");
    logout.setOnClickListener(v -> {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_EMAIL)
                .remove(KEY_BASE_URL)
                .apply();

        token = "";
        email = "";
        baseUrl = "";

        showFirstScreen();
    });

    quick.addView(gallery, fullParams());
    quick.addView(upload, fullParams());
    quick.addView(account, fullParams());
    quick.addView(backup, fullParams());
    quick.addView(admin, fullParams());
    quick.addView(logout, fullParams());
    root.addView(quick, fullParams());

    new Thread(() -> {
        try {
            JSONObject d = getJson("/api/native/dashboard");

            int percent = Math.max(0, Math.min(100, d.optInt("percent", d.optInt("usedPercent", 0))));
            String usedText = d.optString("usedText", d.optString("used", "--"));
            String quotaText = d.optString("quotaText", d.optString("quota", "--"));
            String freeText = d.optString("freeText", d.optString("free", "--"));

            int files = d.optInt("files", d.optInt("totalFiles", 0));
            int photos = d.optInt("photos", d.optInt("totalPhotos", 0));
            int videos = d.optInt("videos", d.optInt("totalVideos", 0));

            String temp = d.optString("temp", d.optString("temperature", d.optString("driveTemp", "--")));

            runOnUiThread(() -> {
                circle.setPercent(percent);
                loading.setText("Storage used: " + usedText + " / " + quotaText + "\nFree: " + freeText);
                filesText.setText("Total files: " + files);
                photosText.setText("Total photos: " + photos);
                videosText.setText("Total videos: " + videos);
                tempText.setText("Drive temperature: " + temp + "°C");
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                loading.setText("Could not load dashboard details.\n" + e.getMessage());
                filesText.setText("Total files: unavailable");
                photosText.setText("Total photos: unavailable");
                videosText.setText("Total videos: unavailable");
                tempText.setText("Drive temperature: unavailable");
            });
        }
    }).start();
}



    private void showLoading(String message) {
        base();

        LinearLayout card = cardBox();
        card.setGravity(Gravity.CENTER);

        TextView t = text(message, 22, Color.rgb(255, 196, 0));
        t.setGravity(Gravity.CENTER);

        card.addView(t);
        root.addView(card, fullParams());
    }

    

private void openPage(Class<?> pageClass) {
    try {
        startActivity(new Intent(this, pageClass));
    } catch (Exception e) {
        toast("Page not ready: " + e.getMessage());
    }
}

private JSONObject getJson(String endpoint) throws Exception {
    URL url = new URL(baseUrl + endpoint);
    HttpURLConnection c = (HttpURLConnection) url.openConnection();

    c.setRequestMethod("GET");
    c.setConnectTimeout(15000);
    c.setReadTimeout(180000);
    c.setRequestProperty("X-FC-Token", token);

    int code = c.getResponseCode();
    String response = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());

    if (code < 200 || code >= 300) {
        throw new Exception(response.length() > 220 ? response.substring(0, 220) : response);
    }

    return new JSONObject(response);
}

private JSONObject postJson(String endpoint, JSONObject body, boolean auth) throws Exception {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();

        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);
        c.setRequestProperty("Content-Type", "application/json");

        if (auth) {
            c.setRequestProperty("X-FC-Token", token);
        }

        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes("UTF-8"));
        }

        int code = c.getResponseCode();
        String response = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());

        if (code < 200 || code >= 300) {
            throw new Exception(response.length() > 220 ? response.substring(0, 220) : response);
        }

        return new JSONObject(response);
    }

    private String readText(InputStream in) throws Exception {
        if (in == null) return "";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;

        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }

        return out.toString("UTF-8");
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private void base() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.setBackgroundColor(Color.rgb(4, 4, 4));

    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    root.setPadding(dp(16), dp(14), dp(16), dp(12));
    root.setBackgroundColor(Color.rgb(4, 4, 4));

    scroll.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));

    setContentView(scroll);
}



    private LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(cardBg(Color.rgb(16, 16, 16), Color.rgb(80, 62, 0)));
        return box;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(6), dp(5), dp(6), dp(5));
        return t;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(145, 145, 145));
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        e.setBackground(cardBg(Color.rgb(24, 24, 24), Color.rgb(70, 70, 70)));
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.BLACK);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(56));
        b.setGravity(Gravity.CENTER);
        b.setBackground(cardBg(Color.rgb(255, 196, 0), Color.rgb(255, 196, 0)));
        return b;
    }

    private Button ghostButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(52));
        b.setGravity(Gravity.CENTER);
        b.setBackground(cardBg(Color.rgb(30, 30, 30), Color.rgb(75, 75, 75)));
        return b;
    }

    private GradientDrawable cardBg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams fullParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(4), dp(7), dp(4), dp(7));
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

public static class CircleProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int percent = 0;

    public CircleProgressView(Activity context) {
        super(context);
    }

    public void setPercent(int value) {
        percent = Math.max(0, Math.min(100, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) - 24;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;

        RectF rect = new RectF(left, top, left + size, top + size);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(14);
        paint.setColor(Color.rgb(48, 48, 48));
        canvas.drawArc(rect, 0, 360, false, paint);

        paint.setColor(Color.rgb(255, 196, 0));
        canvas.drawArc(rect, -90, percent * 3.6f, false, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(39);
        canvas.drawText(percent + "%", w / 2f, h / 2f + 9, paint);

        paint.setColor(Color.LTGRAY);
        paint.setTextSize(15);
        canvas.drawText("used", w / 2f, h / 2f + 36, paint);
    }
}
}
