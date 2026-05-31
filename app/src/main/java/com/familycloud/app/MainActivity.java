package com.familycloud.app;

import android.graphics.RectF;

import android.graphics.Paint;

import android.graphics.Canvas;

import android.widget.ImageView;

import android.widget.ProgressBar;

import android.widget.CheckBox;

import android.widget.FrameLayout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.*;
import android.widget.GridLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String NATIVE_LOGIN_ENDPOINT = "/api/native/login";
    private String adminCode = "";

    private ArrayList<JSONObject> galleryItemsV2 = new ArrayList<>();
    private HashSet<String> selectedGalleryKeysV2 = new HashSet<>();
    private boolean gallerySelectModeV2 = false;

    // Public compatibility constants used by older native worker/helper classes.
    public static final String PREFS = "family_cloud_native";
    public static final String KEY_COOKIE = "cookie";
    public static final String KEY_BASE_URL = "baseUrl";
    public static final String KEY_ONLINE_URL = "baseUrl";
    public static final String ONLINE_URL = "http://100.109.57.8:3000";
    public static final String DEFAULT_ONLINE_URL = "http://100.109.57.8:3000";

    private static final String DEFAULT_URL = "http://100.109.57.8:3000";
    private static final int PICK_UPLOAD = 7001;

    private final int BG = Color.rgb(5, 0, 0);
    private final int PANEL = Color.rgb(13, 0, 0);
    private final int TOP = Color.rgb(17, 17, 17);
    private final int YELLOW = Color.rgb(255, 190, 0);
    private final int BORDER = Color.rgb(166, 106, 0);
    private final int WHITE = Color.WHITE;
    private final int GREEN = Color.rgb(46, 204, 113);
    private final int RED = Color.rgb(230, 43, 43);

    private SharedPreferences prefs;
    private ExecutorService io;
    private LinearLayout root;
    private LinearLayout content;
    private String baseUrl;
    private String cookie;
    private String currentEmail = "";
    private boolean offlineMode = false;

    private ArrayList<JSONObject> allMedia = new ArrayList<>();
    private ArrayList<JSONObject> filteredMedia = new ArrayList<>();
    private HashSet<String> selected = new HashSet<>();
    private int page = 1;
    private int pageSize = 30;
    private String filter = "all";
    private boolean selectMode = false;

    private TextView pageInfoTop;
    private TextView pageInfoBottom;
    private TextView galleryStatus;
    private GridLayout galleryGrid;
    private LinearLayout floatingSelectBar;

    private int previewIndex = -1;

    @Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    baseUrl = prefs.getString(KEY_BASE_URL, "");
    token = prefs.getString(KEY_TOKEN, "");
    email = prefs.getString(KEY_EMAIL, "");
    adminCode = prefs.getString("adminCode", "");
    trimCacheAsync();

    if (token.length() > 8 && baseUrl.length() > 8) showDashboard();
    else showModePicker();
}

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String text, int size, int color, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(null, style);
        t.setPadding(dp(6), dp(4), dp(6), dp(4));
        return t;
    }

    private Button btn(String text, int bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(bg == YELLOW || bg == GREEN ? Color.BLACK : Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(null, 1);
        b.setBackgroundColor(bg);
        b.setAllCaps(false);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        return b;
    }

    private EditText input(String hint, boolean pass) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.GRAY);
        e.setTextColor(WHITE);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackgroundColor(Color.rgb(8, 0, 0));
        if (pass) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        l.setLayoutParams(lp);
        return l;
    }

    private void baseScreen(String title, boolean nav) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8), dp(8), dp(8), dp(88));
        setContentView(root);

        if (nav) addTopbar();

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(30));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView h = tv(title, 28, YELLOW, 1);
        content.addView(h);
    }

    private void addTopbar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(TOP);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        hsv.addView(bar);
        root.addView(hsv, new LinearLayout.LayoutParams(-1, -2));

        navButton(bar, "←\nBack", new View.OnClickListener() { public void onClick(View v) { onBackPressed(); }});
        navButton(bar, "⌂\nHome", new View.OnClickListener() { public void onClick(View v) { showDashboard(); }});
        navButton(bar, "▧\nGallery", new View.OnClickListener() { public void onClick(View v) { showGallery(); }});
        navButton(bar, "☁\nUpload", new View.OnClickListener() { public void onClick(View v) { showUpload(); }});
        navButton(bar, "♙\nAccount", new View.OnClickListener() { public void onClick(View v) { showAccount(); }});
        navButton(bar, "♜\nAdmin", new View.OnClickListener() { public void onClick(View v) { showAdmin(); }});
        navButton(bar, "⇥\nLogout", new View.OnClickListener() { public void onClick(View v) { logout(); }});
    }

    private void navButton(LinearLayout bar, String text, View.OnClickListener l) {
        Button b = btn(text, TOP);
        b.setTextColor(WHITE);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(86), dp(62));
        lp.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(b, lp);
    }

    private void toast(final String s) {
        runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); }});
    }

    private void runIo(final Job j) {
        io.execute(new Runnable() {
            public void run() {
                try { j.run(); }
                catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { alert("Error", e.getMessage()); }
                    });
                }
            }
        });
    }

    interface Job { void run() throws Exception; }

    private String url(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        return baseUrl.replaceAll("/+$", "") + path;
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private String request(String method, String path, String body, String type) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url(path)).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Accept", "application/json,text/plain,*/*");
        if (cookie != null && cookie.length() > 0) c.setRequestProperty("Cookie", cookie);

        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", type);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }

        List<String> set = c.getHeaderFields().get("Set-Cookie");
        if (set != null && !set.isEmpty()) {
            StringBuilder sb = new StringBuilder(cookie == null ? "" : cookie);
            for (String s : set) {
                String one = s.split(";", 2)[0];
                if (sb.indexOf(one.split("=")[0] + "=") < 0) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(one);
                }
            }
            cookie = sb.toString();
            prefs.edit().putString("cookie", cookie).apply();
        }

        int code = c.getResponseCode();
        String out = readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new IOException("HTTP " + code + ": " + out);
        return out;
    }

private void addAdminDrive(JSONObject d) {
    String id = d.optString("id", d.optString("driveId", d.optString("path", d.optString("device", ""))));
    String name = d.optString("nickname", d.optString("name", id));

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(12, 12, 12, 12);
    box.setBackgroundColor(Color.rgb(22, 22, 22));

    box.addView(text("💽 " + name, 17, Color.WHITE));
    box.addView(text("ID: " + id, 12, Color.GRAY));
    box.addView(text("Temp: " + d.optString("temp", d.optString("temperature", "unknown")) + "°C", 14, Color.rgb(255, 152, 0)));
    box.addView(text("Free: " + d.optString("freeText", "?"), 14, Color.LTGRAY));

    EditText nick = input("Drive nickname", name);
    Button save = button("Save Nickname");

    save.setOnClickListener(v -> saveDriveNickname(id, nick.getText().toString().trim()));

    box.addView(nick);
    box.addView(save);
    root.addView(box);
}

private void addAdminUser(JSONObject u, JSONArray drives) {
    String em = u.optString("email");

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(12, 12, 12, 12);
    box.setBackgroundColor(Color.rgb(22, 22, 22));

    box.addView(text(em, 16, Color.WHITE));
    box.addView(text(u.optString("usedText") + " / " + u.optString("quotaText") + " · " + u.optInt("files") + " files", 14, Color.LTGRAY));
    box.addView(text("Drive: " + u.optString("driveNickname", u.optString("driveName", "")), 14, Color.YELLOW));

    EditText quota = input("Quota GB", String.valueOf(u.optDouble("quotaGB", 30)));
    Button saveQuota = button("Save Quota");

    saveQuota.setOnClickListener(v -> saveUserQuota(em, quota.getText().toString().trim()));

    box.addView(quota);
    box.addView(saveQuota);

    for (int i = 0; i < drives.length(); i++) {
        JSONObject d = drives.optJSONObject(i);
        if (d == null) continue;

        String id = d.optString("id", d.optString("driveId", d.optString("path", d.optString("device", ""))));
        String dn = d.optString("nickname", d.optString("name", id));

        Button move = button("Move to " + dn);
        move.setOnClickListener(v -> moveUserDisk(em, id));
        box.addView(move);
    }

    root.addView(box);
}

private void saveDriveNickname(String driveId, String nickname) {
    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("code", adminCode);
            b.put("driveId", driveId);
            b.put("nickname", nickname);

            JSONObject r = post("/api/native/admin/drive-nickname", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "save failed"));

            toast("Drive nickname saved");
            runOnUiThread(this::showAdmin);
        } catch (Exception e) {
            toast("Drive nickname failed: " + e.getMessage());
        }
    }).start();
}

private void saveUserQuota(String em, String quotaGB) {
    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("code", adminCode);
            b.put("email", em);
            b.put("quotaGB", quotaGB);

            JSONObject r = post("/api/native/admin/quota", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "quota failed"));

            toast("Quota saved");
            runOnUiThread(this::showAdmin);
        } catch (Exception e) {
            toast("Quota failed: " + e.getMessage());
        }
    }).start();
}

private void moveUserDisk(String em, String driveId) {
    new AlertDialog.Builder(this)
            .setTitle("Move user to disk?")
            .setMessage(em + "\nDisk: " + driveId)
            .setPositiveButton("Move", (d, w) -> new Thread(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("code", adminCode);
                    b.put("email", em);
                    b.put("driveId", driveId);

                    JSONObject r = post("/api/native/admin/user-disk", b, true);
                    if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "move failed"));

                    toast("User moved");
                    runOnUiThread(this::showAdmin);
                } catch (Exception e) {
                    toast("Move failed: " + e.getMessage());
                }
            }).start())
            .setNegativeButton("Cancel", null)
            .show();
}

private void startZipProcess() {
    Dialog dialog = new Dialog(this);

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(24, 24, 24, 24);
    box.setBackgroundColor(Color.rgb(10, 10, 10));

    TextView title = text("Creating ZIP Backup", 22, Color.WHITE);
    TextView status = text("Starting ZIP job...", 15, Color.LTGRAY);

    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100);
    bar.setIndeterminate(true);

    Button open = button("Open ZIP Link");
    open.setVisibility(View.GONE);

    Button cancel = button("Cancel ZIP Process");

    final String[] jobId = new String[]{""};
    final boolean[] done = new boolean[]{false};

    cancel.setOnClickListener(v -> {
        if (!done[0] && jobId[0].length() > 0) cancelZipJob(jobId[0]);
        dialog.dismiss();
    });

    box.addView(title);
    box.addView(status);
    box.addView(bar);
    box.addView(open);
    box.addView(cancel);

    dialog.setContentView(box);
    dialog.setOnCancelListener(d -> {
        if (!done[0] && jobId[0].length() > 0) cancelZipJob(jobId[0]);
    });
    dialog.show();

    new Thread(() -> {
        try {
            JSONObject start = post("/api/native/account/zip-start", new JSONObject(), true);
            if (!start.optBoolean("ok")) throw new Exception(start.optString("error", "zip start failed"));

            jobId[0] = start.optString("id", "");
            if (jobId[0].length() == 0) throw new Exception("server returned no ZIP job id");

            while (!done[0]) {
                JSONObject r = get("/api/native/account/zip-status/" + URLEncoder.encode(jobId[0], "UTF-8"));
                String st = r.optString("status", "");
                int pct = r.optInt("progress", 0);

                runOnUiThread(() -> {
                    bar.setIndeterminate(false);
                    bar.setProgress(Math.max(0, Math.min(100, pct)));
                    status.setText("ZIP status: " + st + " · " + pct + "%");
                });

                if ("ready".equalsIgnoreCase(st)) {
                    done[0] = true;
                    String link = absoluteUrl(r.optString("url", r.optString("downloadUrl", "")));
                    String expires = r.optString("expiresText", "");

                    runOnUiThread(() -> {
                        bar.setProgress(100);
                        status.setText("ZIP complete.\nValid until: " + expires + "\n" + link);
                        open.setVisibility(View.VISIBLE);
                        open.setOnClickListener(v -> openLink(link));
                        cancel.setText("Close");
                        cancel.setOnClickListener(v -> dialog.dismiss());
                    });
                    return;
                }

                if ("failed".equalsIgnoreCase(st) || "cancelled".equalsIgnoreCase(st)) {
                    done[0] = true;
                    runOnUiThread(() -> {
                        status.setText("ZIP " + st + ": " + r.optString("error", ""));
                        cancel.setText("Close");
                        cancel.setOnClickListener(v -> dialog.dismiss());
                    });
                    return;
                }

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            done[0] = true;
            runOnUiThread(() -> {
                bar.setIndeterminate(false);
                status.setText("ZIP failed: " + e.getMessage());
                cancel.setText("Close");
                cancel.setOnClickListener(v -> dialog.dismiss());
            });
        }
    }).start();
}

private void cancelZipJob(String id) {
    new Thread(() -> {
        try {
            post("/api/native/account/zip-cancel/" + URLEncoder.encode(id, "UTF-8"), new JSONObject(), true);
            toast("ZIP cancelled and temp file deleted");
        } catch (Exception e) {
            toast("ZIP cancel failed: " + e.getMessage());
        }
    }).start();
}

private void showChangePasswordDialog() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(16, 16, 16, 16);

    EditText oldP = input("Current password", "");
    EditText newP = input("New password", "");
    EditText confirm = input("Confirm new password", "");

    oldP.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    newP.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    box.addView(oldP);
    box.addView(newP);
    box.addView(confirm);

    new AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(box)
            .setPositiveButton("Change", (d, w) -> changePassword(oldP.getText().toString(), newP.getText().toString(), confirm.getText().toString()))
            .setNegativeButton("Cancel", null)
            .show();
}

private void changePassword(String oldPass, String newPass, String confirm) {
    if (!newPass.equals(confirm)) {
        toast("Passwords do not match");
        return;
    }

    if (newPass.length() < 6) {
        toast("Password must be at least 6 characters");
        return;
    }

    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("oldPassword", oldPass);
            b.put("newPassword", newPass);

            JSONObject r = post("/api/native/account/change-password", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "password failed"));

            toast("Password changed");
        } catch (Exception e) {
            toast("Password failed: " + e.getMessage());
        }
    }).start();
}

private String cleanUrl(String url) {
    if (url == null) return "";
    url = url.trim();
    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    return url;
}

private String absoluteUrl(String url) {
    if (url == null) return "";
    if (url.startsWith("http://") || url.startsWith("https://")) return url;
    if (url.startsWith("/")) return baseUrl + url;
    return baseUrl + "/" + url;
}

private void openLink(String link) {
    try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
    } catch (Exception e) {
        toast("Cannot open ZIP link");
    }
}

public static class CircleProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int percent = 0;

    public CircleProgressView(Activity context) {
        super(context);
    }

    public void setPercent(int p) {
        percent = Math.max(0, Math.min(100, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) - 20;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;

        RectF rect = new RectF(left, top, left + size, top + size);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(12);
        paint.setColor(Color.rgb(55, 55, 55));
        canvas.drawArc(rect, 0, 360, false, paint);

        paint.setColor(Color.rgb(255, 196, 0));
        canvas.drawArc(rect, -90, percent * 3.6f, false, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(34);
        canvas.drawText(percent + "%", w / 2f, h / 2f + 8, paint);
    }
}

private JSONObject getJson(String path) throws Exception {
        return new JSONObject(request("GET", path, null, null));
    }

    private JSONObject postJson(String path, JSONObject obj) throws Exception {
        return new JSONObject(request("POST", path, obj.toString(), "application/json"));
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private String q(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) return String.valueOf(o.opt(k));
        }
        return "";
    }

    private int qi(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k)) return o.optInt(k, 0);
        }
        return 0;
    }

    private void alert(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg == null ? "" : msg).setPositiveButton("OK", null).show();
    }

    private void showModeScreen() {
        baseScreen("Family Cloud", false);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(tv("Choose how to open the native app.", 16, WHITE, 0));

        Button online = btn("Online Native Mode", YELLOW);
        Button offline = btn("Offline Mode", TOP);
        Button edit = btn("Edit Saved Server URL", TOP);

        content.addView(online, new LinearLayout.LayoutParams(-1, dp(58)));
        content.addView(offline, new LinearLayout.LayoutParams(-1, dp(58)));
        content.addView(edit, new LinearLayout.LayoutParams(-1, dp(58)));

        content.addView(tv("Saved URL: " + baseUrl, 13, Color.LTGRAY, 0));

        online.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showUrlChoice(); }
        });

        offline.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                offlineMode = true;
                showOffline();
            }
        });

        edit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showEditUrl(); }
        });
    }

    private void showUrlChoice() {
        baseScreen("Online Native Mode", false);
        content.addView(tv("Saved server:", 16, YELLOW, 1));
        content.addView(tv(baseUrl, 14, WHITE, 0));

        Button cont = btn("Continue With Saved URL", GREEN);
        Button edit = btn("Edit Saved URL", YELLOW);
        Button back = btn("Go Back", TOP);
        content.addView(cont, new LinearLayout.LayoutParams(-1, dp(58)));
        content.addView(edit, new LinearLayout.LayoutParams(-1, dp(58)));
        content.addView(back, new LinearLayout.LayoutParams(-1, dp(58)));

        cont.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                offlineMode = false;
                checkSessionOrLogin();
            }
        });
        edit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showEditUrl(); }});
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showModeScreen(); }});
    }

    private void showEditUrl() {
        baseScreen("Edit Server URL", false);
        final EditText e = input("http://100.x.x.x:3000", false);
        e.setText(baseUrl);
        content.addView(e);
        Button save = btn("Save URL", GREEN);
        Button back = btn("Back", TOP);
        content.addView(save);
        content.addView(back);

        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = e.getText().toString().trim();
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    alert("Wrong URL", "Use http://IP:3000");
                    return;
                }
                baseUrl = u;
                prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
                showUrlChoice();
            }
        });

        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showModeScreen(); }});
    }

    private void checkSessionOrLogin() {
        baseScreen("Checking Login", false);
        content.addView(tv("Checking saved login session...", 16, WHITE, 0));
        runIo(new Job() {
            public void run() throws Exception {
                try {
                    JSONObject u = getJson("/api/current-user");
                    String email = q(u, "email", "user", "username");
                    if (email.contains("@")) {
                        currentEmail = email;
                        runOnUiThread(new Runnable() { public void run() { showDashboard(); }});
                        return;
                    }
                } catch (Exception ignored) {}
                runOnUiThread(new Runnable() { public void run() { showLogin(); }});
            }
        });
    }

    private void showLogin() {
    showModePicker();
}

    private void logout() {
    prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .apply();

    token = "";
    email = "";
    showModePicker();
}

    private void showOffline() {
        baseScreen("Offline Mode", false);
        content.addView(tv("Offline mode can show saved server URL and cached login only. Live gallery/upload/admin need server.", 15, WHITE, 0));
        content.addView(tv("Saved URL: " + baseUrl, 14, YELLOW, 1));
        Button back = btn("Back", TOP);
        content.addView(back);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showModeScreen(); }});
    }

    private void showDashboard() {
    base("Dashboard");
    nav();
    root.addView(text("Loading dashboard...", 16, Color.YELLOW));

    new Thread(() -> {
        try {
            JSONObject d = get("/api/native/dashboard");
            runOnUiThread(() -> {
                base("Dashboard");
                nav();

                int percent = Math.max(0, Math.min(100, d.optInt("percent", 0)));

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.VERTICAL);
                top.setPadding(12, 12, 12, 12);
                top.setGravity(Gravity.CENTER_HORIZONTAL);
                top.setBackgroundColor(Color.rgb(18, 18, 18));

                top.addView(text("Email: " + email, 15, Color.YELLOW));

                CircleProgressView circle = new CircleProgressView(this);
                circle.setPercent(percent);
                top.addView(circle, new LinearLayout.LayoutParams(180, 180));

                top.addView(text("Storage: " + d.optString("usedText") + " / " + d.optString("quotaText"), 18, Color.WHITE));
                top.addView(text("Free: " + d.optString("freeText"), 15, Color.LTGRAY));
                root.addView(top);

                root.addView(text("Files: " + d.optInt("files") + "    Photos: " + d.optInt("photos") + "    Videos: " + d.optInt("videos"), 16, Color.WHITE));
                root.addView(text("Drive Temp: " + d.optString("temp", "unknown") + "°C", 16, Color.rgb(255, 152, 0)));
            });
        } catch (Exception e) {
            toast("Dashboard error: " + e.getMessage());
        }
    }).start();
}

    private JSONObject safeGet(String path) {
        try { return getJson(path); } catch (Exception e) { return new JSONObject(); }
    }

    private LinearLayout.LayoutParams panelLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        return lp;
    }

    private View card(View inner) {
        LinearLayout p = panel();
        p.addView(inner);
        return p;
    }

    private void showGallery(int page, String type) {
    currentPage = Math.max(1, page);
    currentType = type == null ? "all" : type;

    base("Gallery");
    nav();

    root.addView(text("Supported photos and videos from your Family Cloud folder.", 15, Color.LTGRAY));
    addGalleryToolbarV2(1);

    TextView loadingText = text("Loading gallery...", 16, Color.YELLOW);
    ProgressBar loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    loadingBar.setIndeterminate(true);

    root.addView(loadingText);
    root.addView(loadingBar, gFullParamsV2());

    new Thread(() -> {
        try {
            JSONObject j = get("/api/native/files?page=" + currentPage + "&limit=48&type=" + URLEncoder.encode(currentType, "UTF-8"));
            JSONArray arr = j.getJSONArray("items");
            int pages = Math.max(1, j.optInt("pages", 1));

            ArrayList<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }

            runOnUiThread(() -> {
                galleryItemsV2 = list;

                base("Gallery");
                nav();

                root.addView(text("Supported photos and videos from your Family Cloud folder.", 15, Color.LTGRAY));
                addGalleryToolbarV2(pages);

                root.addView(text("Showing " + list.size() + " items · " + selectedGalleryKeysV2.size() + " selected", 14, Color.YELLOW));

                addGalleryPagerV2(pages);

                if (list.isEmpty()) {
                    root.addView(text("No files found on this page.", 16, Color.LTGRAY));
                } else {
                    addGalleryGridV2(list);
                }

                addGalleryPagerV2(pages);
            });
        } catch (Exception e) {
            toast("Gallery error: " + e.getMessage());
        }
    }).start();
}

    private LinearLayout pagerBar() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        Button prev = btn("Previous", TOP);
        Button next = btn("Next", TOP);
        TextView info = tv("Page 1 / 1", 15, YELLOW, 1);
        if (pageInfoTop == null) pageInfoTop = info; else pageInfoBottom = info;

        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (page > 1) { page--; renderGallery(); } }});
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            int max = Math.max(1, (int)Math.ceil(filteredMedia.size() / (double)pageSize));
            if (page < max) { page++; renderGallery(); }
        }});

        l.addView(prev, new LinearLayout.LayoutParams(0, dp(54), 1));
        l.addView(info, new LinearLayout.LayoutParams(0, dp(54), 1));
        l.addView(next, new LinearLayout.LayoutParams(0, dp(54), 1));
        return l;
    }

    private void loadGallery() {
        galleryStatus.setText("Loading file list...");
        runIo(new Job() {
            public void run() throws Exception {
                JSONObject data = getJson("/api/files");
                JSONArray arr = data.optJSONArray("files");
                allMedia.clear();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.optJSONObject(i);
                        if (f == null) continue;
                        if (isPhoto(f) || isVideo(f)) allMedia.add(f);
                    }
                }
                final String st = "User: " + q(data, "user") + "\nRoot: " + q(data, "root") + "\nSupported media: " + allMedia.size() + "\nPage size: 30";
                runOnUiThread(new Runnable() {
                    public void run() {
                        galleryStatus.setText(st);
                        applyGalleryFilter();
                    }
                });
            }
        });
    }

    private boolean isPhoto(JSONObject f) {
        String x = (q(f, "mime", "type") + " " + q(f, "name", "filename", "path")).toLowerCase();
        return x.contains("image/") || x.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|tif|tiff)$");
    }

    private boolean isVideo(JSONObject f) {
        String x = (q(f, "mime", "type") + " " + q(f, "name", "filename", "path")).toLowerCase();
        return x.contains("video/") || x.matches(".*\\.(mp4|mov|m4v|3gp|webm|mkv|avi)$");
    }

    private void applyGalleryFilter() {
        filteredMedia.clear();
        for (JSONObject f : allMedia) {
            if ("photo".equals(filter) && !isPhoto(f)) continue;
            if ("video".equals(filter) && !isVideo(f)) continue;
            filteredMedia.add(f);
        }
        renderGallery();
    }

    private void renderGallery() {
        if (galleryGrid == null) return;
        galleryGrid.removeAllViews();
        int maxPage = Math.max(1, (int)Math.ceil(filteredMedia.size() / (double)pageSize));
        if (page > maxPage) page = maxPage;
        if (page < 1) page = 1;

        String info = "Page " + page + " / " + maxPage;
        if (pageInfoTop != null) pageInfoTop.setText(info);
        if (pageInfoBottom != null) pageInfoBottom.setText(info);

        int start = (page - 1) * pageSize;
        int end = Math.min(filteredMedia.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            final int index = i;
            JSONObject f = filteredMedia.get(i);
            LinearLayout tile = panel();
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = getResources().getDisplayMetrics().widthPixels / 3 - dp(14);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            tile.setLayoutParams(glp);

            String name = fileName(f);
            String key = itemKey(f);
            TextView label = tv((selected.contains(key) ? "✓ " : "") + name, 10, selected.contains(key) ? GREEN : WHITE, 1);

            if (isPhoto(f)) {
                final ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setBackgroundColor(Color.BLACK);
                tile.addView(img, new LinearLayout.LayoutParams(-1, dp(110)));
                loadBitmapInto(img, fileUrl(f), true);
            } else {
                TextView v = tv("🎥\nVIDEO", 18, YELLOW, 1);
                v.setGravity(Gravity.CENTER);
                tile.addView(v, new LinearLayout.LayoutParams(-1, dp(110)));
            }

            tile.addView(label);
            tile.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (selectMode) {
                        JSONObject f = filteredMedia.get(index);
                        String k = itemKey(f);
                        if (selected.contains(k)) selected.remove(k); else selected.add(k);
                        renderGallery();
                    } else {
                        openPreview(index);
                    }
                }
            });

            galleryGrid.addView(tile);
        }
        updateFloatingBar();
    }

    private void updateFloatingBar() {
        if (floatingSelectBar == null) return;
        floatingSelectBar.removeAllViews();
        if (!selectMode || selected.isEmpty()) {
            floatingSelectBar.setVisibility(View.GONE);
            return;
        }
        floatingSelectBar.setVisibility(View.VISIBLE);
        floatingSelectBar.addView(tv(selected.size() + " selected", 13, YELLOW, 1));
        Button down = btn("Download", YELLOW);
        Button share = btn("Share", TOP);
        Button del = btn("Delete", RED);
        Button clear = btn("Clear", TOP);
        floatingSelectBar.addView(down);
        floatingSelectBar.addView(share);
        floatingSelectBar.addView(del);
        floatingSelectBar.addView(clear);

        down.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { downloadSelected(); }});
        share.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareSelected(); }});
        del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { deleteSelected(); }});
        clear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selected.clear(); renderGallery(); }});
    }

    private String fileName(JSONObject f) { String n = q(f, "name", "filename", "path"); return n.length() == 0 ? "file" : n; }
    private String fileUrl(JSONObject f) { String u = q(f, "url"); return u.startsWith("http") ? u : url(u); }
    private String itemKey(JSONObject f) { String p = q(f, "path", "name", "filename", "url"); return p.length() == 0 ? String.valueOf(f.hashCode()) : p; }

    private ArrayList<JSONObject> selectedItems() {
        ArrayList<JSONObject> out = new ArrayList<>();
        for (JSONObject f : allMedia) if (selected.contains(itemKey(f))) out.add(f);
        return out;
    }

    private void loadBitmapInto(final ImageView view, final String u, final boolean thumb) {
        io.execute(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
                    if (cookie != null && cookie.length() > 0) c.setRequestProperty("Cookie", cookie);
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(30000);
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    if (thumb) opt.inSampleSize = 4;
                    final Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream(), null, opt);
                    if (bmp != null) runOnUiThread(new Runnable() { public void run() { view.setImageBitmap(bmp); }});
                } catch (Exception ignored) {}
            }
        });
    }

    private void openPreview(int index) {
        previewIndex = index;
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(makePreviewView(d));
        Window w = d.getWindow();
        d.show();
        Window win = d.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            win.setBackgroundDrawableResource(android.R.color.black);
        }
    }

    private View makePreviewView(final Dialog dialog) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.BLACK);
        box.setPadding(dp(8), dp(28), dp(8), dp(18));

        final TextView counter = tv("", 13, YELLOW, 1);
        counter.setGravity(Gravity.CENTER);
        box.addView(counter);

        final FrameLayout stage = new FrameLayout(this);
        box.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        Button prev = btn("Previous", TOP);
        Button next = btn("Next", TOP);
        Button zoomIn = btn("Zoom +", TOP);
        Button zoomOut = btn("Zoom -", TOP);
        Button dl = btn("Download", YELLOW);
        Button sh = btn("Share", TOP);
        Button del = btn("Delete", RED);
        Button close = btn("Close", TOP);

        actions.addView(prev);
        actions.addView(next);
        actions.addView(zoomIn);
        actions.addView(zoomOut);
        actions.addView(dl);
        actions.addView(sh);
        actions.addView(del);
        actions.addView(close);

        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            public void run() {
                stage.removeAllViews();
                if (previewIndex < 0) previewIndex = filteredMedia.size() - 1;
                if (previewIndex >= filteredMedia.size()) previewIndex = 0;
                JSONObject f = filteredMedia.get(previewIndex);
                counter.setText((previewIndex + 1) + " / " + filteredMedia.size() + " · " + fileName(f));

                if (isPhoto(f)) {
                    ZoomImageView img = new ZoomImageView(MainActivity.this);
                    img.setBackgroundColor(Color.BLACK);
                    img.onSwipeLeft = new Runnable() { public void run() { previewIndex++; render[0].run(); }};
                    img.onSwipeRight = new Runnable() { public void run() { previewIndex--; render[0].run(); }};
                    stage.addView(img, new FrameLayout.LayoutParams(-1, -1));
                    loadBitmapInto(img, fileUrl(f), false);
                    zoomIn.setVisibility(View.VISIBLE);
                    zoomOut.setVisibility(View.VISIBLE);
                    zoomIn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        View child = stage.getChildAt(0);
                        if (child instanceof ZoomImageView) ((ZoomImageView)child).zoomIn();
                    }});
                    zoomOut.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        View child = stage.getChildAt(0);
                        if (child instanceof ZoomImageView) ((ZoomImageView)child).zoomOut();
                    }});
                } else {
                    TextView video = tv("🎥\nVideo\nUse Download or Share to open/play.", 24, WHITE, 1);
                    video.setGravity(Gravity.CENTER);
                    stage.addView(video, new FrameLayout.LayoutParams(-1, -1));
                    zoomIn.setVisibility(View.GONE);
                    zoomOut.setVisibility(View.GONE);
                }
            }
        };

        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { previewIndex--; render[0].run(); }});
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { previewIndex++; render[0].run(); }});
        dl.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { downloadFile(filteredMedia.get(previewIndex)); }});
        sh.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareFile(filteredMedia.get(previewIndex)); }});
        del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { deleteOne(filteredMedia.get(previewIndex)); dialog.dismiss(); }});
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dialog.dismiss(); }});

        render[0].run();
        return box;
    }

    public static class ZoomImageView extends ImageView {
        Matrix matrix = new Matrix();
        float scale = 1f, tx = 0f, ty = 0f;
        float sx, sy;
        long lastTap = 0;
        Runnable onSwipeLeft, onSwipeRight;

        public ZoomImageView(Context c) {
            super(c);
            setScaleType(ScaleType.MATRIX);
            setAdjustViewBounds(true);
        }

        public void zoomIn() { scale = Math.min(5f, scale + .5f); apply(); }
        public void zoomOut() { scale = Math.max(1f, scale - .5f); if (scale == 1f) { tx = 0; ty = 0; } apply(); }

        private void apply() {
            matrix.reset();
            matrix.postScale(scale, scale, getWidth()/2f, getHeight()/2f);
            matrix.postTranslate(tx, ty);
            setImageMatrix(matrix);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                sx = e.getX(); sy = e.getY();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE && scale > 1f) {
                tx += e.getX() - sx;
                ty += e.getY() - sy;
                sx = e.getX(); sy = e.getY();
                apply();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - sx;
                float dy = e.getY() - sy;
                long now = System.currentTimeMillis();
                if (now - lastTap < 300) {
                    if (scale > 1f) { scale = 1f; tx = 0; ty = 0; }
                    else scale = 2.5f;
                    apply();
                    lastTap = 0;
                    return true;
                }
                lastTap = now;
                if (scale <= 1f && Math.abs(dx) > 80 && Math.abs(dy) < 90) {
                    if (dx < 0 && onSwipeLeft != null) onSwipeLeft.run();
                    if (dx > 0 && onSwipeRight != null) onSwipeRight.run();
                }
            }
            return true;
        }
    }

    private void downloadSelected() {
        for (JSONObject f : selectedItems()) downloadFile(f);
    }

    private void downloadFile(JSONObject f) {
        try {
            DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(fileUrl(f)));
            r.setTitle(fileName(f));
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName(f));
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (cookie != null && cookie.length() > 0) r.addRequestHeader("Cookie", cookie);
            dm.enqueue(r);
            toast("Download started");
        } catch (Exception e) { alert("Download failed", e.getMessage()); }
    }

    private void shareSelected() {
        ArrayList<JSONObject> items = selectedItems();
        if (items.isEmpty()) return;
        if (items.size() == 1) { shareFile(items.get(0)); return; }
        StringBuilder sb = new StringBuilder();
        for (JSONObject f : items) sb.append(fileUrl(f)).append("\n");
        shareText("Family Cloud files", sb.toString());
    }

    private void shareFile(JSONObject f) {
        shareText(fileName(f), fileUrl(f));
    }

    private void shareText(String title, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) {
            ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(title, text));
            toast("Copied link");
        }
    }

    private void deleteSelected() {
        final ArrayList<JSONObject> items = selectedItems();
        if (items.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete selected?")
                .setMessage("Delete " + items.size() + " file(s)? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        runIo(new Job() {
                            public void run() throws Exception {
                                for (JSONObject f : items) deleteFileRequest(f);
                                selected.clear();
                                runOnUiThread(new Runnable() { public void run() { loadGallery(); }});
                            }
                        });
                    }
                })
                .show();
    }

    private void deleteOne(final JSONObject f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage(fileName(f))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        runIo(new Job() {
                            public void run() throws Exception {
                                deleteFileRequest(f);
                                runOnUiThread(new Runnable() { public void run() { loadGallery(); }});
                            }
                        });
                    }
                })
                .show();
    }

    private void deleteFileRequest(JSONObject f) throws Exception {
        JSONObject body = new JSONObject();
        body.put("path", q(f, "path", "name", "filename"));
        postJson("/api/delete-file", body);
    }

// FC_NATIVE_GALLERY_FINAL_START

private interface GalleryProgressV2 {
    void onProgress(long done, long total);
}

private int gDpV2(int v) {
    return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
}

private LinearLayout.LayoutParams gWeightParamsV2() {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    lp.setMargins(gDpV2(5), gDpV2(5), gDpV2(5), gDpV2(5));
    return lp;
}

private LinearLayout.LayoutParams gFullParamsV2() {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(gDpV2(6), gDpV2(6), gDpV2(6), gDpV2(6));
    return lp;
}

private Button gButtonV2(String label) {
    Button b = button(label);
    b.setMinHeight(gDpV2(48));
    b.setPadding(gDpV2(8), gDpV2(8), gDpV2(8), gDpV2(8));
    return b;
}

private void addGalleryToolbarV2(int pages) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);

    Button refresh = gButtonV2("Refresh");
    Button select = gButtonV2(gallerySelectModeV2 ? "Done" : "Select");
    Button all = gButtonV2("All");
    Button photos = gButtonV2("Photos");
    Button videos = gButtonV2("Videos");

    refresh.setOnClickListener(v -> showGallery(currentPage, currentType));

    select.setOnClickListener(v -> {
        gallerySelectModeV2 = !gallerySelectModeV2;
        if (!gallerySelectModeV2) selectedGalleryKeysV2.clear();
        showGallery(currentPage, currentType);
    });

    all.setOnClickListener(v -> showGallery(1, "all"));
    photos.setOnClickListener(v -> showGallery(1, "photo"));
    videos.setOnClickListener(v -> showGallery(1, "video"));

    row.addView(refresh, gWeightParamsV2());
    row.addView(select, gWeightParamsV2());
    row.addView(all, gWeightParamsV2());
    row.addView(photos, gWeightParamsV2());
    row.addView(videos, gWeightParamsV2());

    root.addView(row);

    if (gallerySelectModeV2) {
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button clear = gButtonV2("Clear");
        Button download = gButtonV2("Download Selected");
        Button delete = gButtonV2("Delete Selected");

        clear.setOnClickListener(v -> {
            selectedGalleryKeysV2.clear();
            showGallery(currentPage, currentType);
        });

        download.setOnClickListener(v -> downloadSelectedGalleryFilesV2());
        delete.setOnClickListener(v -> confirmDeleteSelectedGalleryFilesV2());

        row2.addView(clear, gWeightParamsV2());
        row2.addView(download, gWeightParamsV2());
        row2.addView(delete, gWeightParamsV2());

        root.addView(row2);
    }
}

private void addGalleryPagerV2(int pages) {
    LinearLayout pager = new LinearLayout(this);
    pager.setOrientation(LinearLayout.HORIZONTAL);
    pager.setGravity(Gravity.CENTER_VERTICAL);

    Button prev = gButtonV2("Previous");
    TextView pageText = text("Page " + currentPage + " / " + pages, 16, Color.YELLOW);
    pageText.setGravity(Gravity.CENTER);
    Button next = gButtonV2("Next");

    prev.setEnabled(currentPage > 1);
    next.setEnabled(currentPage < pages);

    prev.setOnClickListener(v -> showGallery(Math.max(1, currentPage - 1), currentType));
    next.setOnClickListener(v -> showGallery(Math.min(pages, currentPage + 1), currentType));

    pager.addView(prev, gWeightParamsV2());
    pager.addView(pageText, gWeightParamsV2());
    pager.addView(next, gWeightParamsV2());

    root.addView(pager);
}

private void addGalleryGridV2(ArrayList<JSONObject> items) {
    for (int i = 0; i < items.size(); i += 3) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, gDpV2(3), 0, gDpV2(3));

        for (int c = 0; c < 3; c++) {
            int index = i + c;

            if (index < items.size()) {
                try {
                    row.addView(galleryCardV2(items.get(index), index), gWeightParamsV2());
                } catch (Exception e) {
                    TextView broken = text("Broken", 12, Color.RED);
                    row.addView(broken, gWeightParamsV2());
                }
            } else {
                TextView empty = text("", 1, Color.TRANSPARENT);
                row.addView(empty, gWeightParamsV2());
            }
        }

        root.addView(row);
    }
}

private View galleryCardV2(JSONObject item, int index) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(gDpV2(5), gDpV2(5), gDpV2(5), gDpV2(5));
    card.setMinimumHeight(gDpV2(190));

    String key = galleryKeyV2(item);
    boolean selected = selectedGalleryKeysV2.contains(key);

    card.setBackgroundColor(selected ? Color.rgb(80, 45, 0) : Color.rgb(18, 18, 18));

    FrameLayout thumbBox = new FrameLayout(this);

    ImageView img = new ImageView(this);
    img.setBackgroundColor(Color.rgb(8, 8, 8));
    img.setScaleType(ImageView.ScaleType.CENTER_CROP);

    thumbBox.addView(img, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            gDpV2(125),
            Gravity.CENTER
    ));

    ProgressBar pb = new ProgressBar(this);
    thumbBox.addView(pb, new FrameLayout.LayoutParams(gDpV2(42), gDpV2(42), Gravity.CENTER));

    CheckBox check = new CheckBox(this);
    check.setVisibility(gallerySelectModeV2 ? View.VISIBLE : View.GONE);
    check.setChecked(selected);
    thumbBox.addView(check, new FrameLayout.LayoutParams(gDpV2(48), gDpV2(48), Gravity.TOP | Gravity.RIGHT));

    TextView play = text("▶", 28, Color.WHITE);
    play.setGravity(Gravity.CENTER);
    play.setVisibility(isVideoItemV2(item) ? View.VISIBLE : View.GONE);
    thumbBox.addView(play, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            gDpV2(125),
            Gravity.CENTER
    ));

    card.addView(thumbBox);

    TextView title = text(shortNameV2(item.optString("name", "file")), 11, Color.WHITE);
    title.setGravity(Gravity.CENTER);
    card.addView(title);

    TextView meta = text(item.optString("sizeText", ""), 10, Color.GRAY);
    meta.setGravity(Gravity.CENTER);
    card.addView(meta);

    if (isPhotoItemV2(item)) {
        loadThumbnailV2(item, img, pb);
    } else if (isVideoItemV2(item)) {
        pb.setVisibility(View.GONE);
        img.setImageResource(android.R.drawable.ic_media_play);
    } else {
        pb.setVisibility(View.GONE);
        img.setImageResource(android.R.drawable.ic_menu_save);
    }

    View.OnClickListener action = v -> {
        if (gallerySelectModeV2) {
            toggleGallerySelectionV2(item);
            showGallery(currentPage, currentType);
        } else {
            if (isPhotoItemV2(item)) showFullPreviewV2(index);
            else openNative(item);
        }
    };

    card.setOnClickListener(action);
    thumbBox.setOnClickListener(action);

    check.setOnClickListener(v -> {
        toggleGallerySelectionV2(item);
        showGallery(currentPage, currentType);
    });

    card.setOnLongClickListener(v -> {
        gallerySelectModeV2 = true;
        toggleGallerySelectionV2(item);
        showGallery(currentPage, currentType);
        return true;
    });

    return card;
}

private String shortNameV2(String name) {
    if (name == null) return "";
    if (name.length() <= 18) return name;
    return name.substring(0, 15) + "...";
}

private String galleryKeyV2(JSONObject item) {
    return item.optString("folder", "") + "/" + item.optString("name", "");
}

private void toggleGallerySelectionV2(JSONObject item) {
    String key = galleryKeyV2(item);
    if (selectedGalleryKeysV2.contains(key)) selectedGalleryKeysV2.remove(key);
    else selectedGalleryKeysV2.add(key);
}

private boolean isPhotoItemV2(JSONObject item) {
    String kind = item.optString("kind", "").toLowerCase(Locale.ROOT);
    String name = item.optString("name", "").toLowerCase(Locale.ROOT);

    return kind.equals("photo")
            || name.endsWith(".jpg")
            || name.endsWith(".jpeg")
            || name.endsWith(".png")
            || name.endsWith(".webp")
            || name.endsWith(".gif")
            || name.endsWith(".bmp")
            || name.endsWith(".heic")
            || name.endsWith(".heif");
}

private boolean isVideoItemV2(JSONObject item) {
    String kind = item.optString("kind", "").toLowerCase(Locale.ROOT);
    String name = item.optString("name", "").toLowerCase(Locale.ROOT);

    return kind.equals("video")
            || name.endsWith(".mp4")
            || name.endsWith(".mov")
            || name.endsWith(".mkv")
            || name.endsWith(".avi")
            || name.endsWith(".webm")
            || name.endsWith(".3gp")
            || name.endsWith(".m4v");
}

private void loadThumbnailV2(JSONObject item, ImageView img, ProgressBar pb) {
    pb.setVisibility(View.VISIBLE);

    new Thread(() -> {
        try {
            File f = downloadFileWithProgressV2(item, "family-cloud-thumbnails", null);
            Bitmap bm = decodeSampledBitmapV2(f.getAbsolutePath(), 360, 360);

            runOnUiThread(() -> {
                pb.setVisibility(View.GONE);
                if (bm != null) img.setImageBitmap(bm);
                else img.setImageResource(android.R.drawable.ic_menu_gallery);
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                pb.setVisibility(View.GONE);
                img.setImageResource(android.R.drawable.ic_menu_report_image);
            });
        }
    }).start();
}

private void showFullPreviewV2(int startIndex) {
    if (galleryItemsV2 == null || galleryItemsV2.isEmpty()) return;

    final int[] index = new int[]{Math.max(0, Math.min(startIndex, galleryItemsV2.size() - 1))};
    final float[] zoom = new float[]{1.0f};

    Dialog dialog = new Dialog(this);

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(gDpV2(8), gDpV2(8), gDpV2(8), gDpV2(8));
    box.setBackgroundColor(Color.BLACK);

    TextView counter = text("", 14, Color.YELLOW);
    counter.setGravity(Gravity.CENTER);

    ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);

    ImageView preview = new ImageView(this);
    preview.setBackgroundColor(Color.BLACK);
    preview.setAdjustViewBounds(true);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

    TextView fileName = text("", 13, Color.WHITE);
    fileName.setGravity(Gravity.CENTER);

    TextView hint = text("Tap image to zoom. Use buttons for exact zoom control.", 11, Color.LTGRAY);
    hint.setGravity(Gravity.CENTER);

    LinearLayout row1 = new LinearLayout(this);
    row1.setOrientation(LinearLayout.HORIZONTAL);

    Button prev = gButtonV2("Previous");
    Button next = gButtonV2("Next");
    Button zoomMinus = gButtonV2("Zoom -");
    Button zoomPlus = gButtonV2("Zoom +");

    row1.addView(prev, gWeightParamsV2());
    row1.addView(next, gWeightParamsV2());
    row1.addView(zoomMinus, gWeightParamsV2());
    row1.addView(zoomPlus, gWeightParamsV2());

    LinearLayout row2 = new LinearLayout(this);
    row2.setOrientation(LinearLayout.HORIZONTAL);

    Button reset = gButtonV2("Reset");
    Button download = gButtonV2("Download");
    Button share = gButtonV2("Share");
    Button delete = gButtonV2("Delete");

    delete.setBackgroundColor(Color.rgb(220, 40, 40));

    row2.addView(reset, gWeightParamsV2());
    row2.addView(download, gWeightParamsV2());
    row2.addView(share, gWeightParamsV2());
    row2.addView(delete, gWeightParamsV2());

    Button close = gButtonV2("Close");

    box.addView(counter);
    box.addView(progress, gFullParamsV2());
    box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gDpV2(470)));
    box.addView(fileName);
    box.addView(hint);
    box.addView(row1);
    box.addView(row2);
    box.addView(close, gFullParamsV2());

    Runnable loadCurrent = () -> {
        JSONObject item = galleryItemsV2.get(index[0]);

        zoom[0] = 1.0f;
        preview.setScaleX(1.0f);
        preview.setScaleY(1.0f);

        counter.setText((index[0] + 1) + " / " + galleryItemsV2.size());
        fileName.setText("Loading preview...");

        loadPreviewIntoV2(item, preview, progress, fileName);
    };

    prev.setOnClickListener(v -> {
        index[0] = Math.max(0, index[0] - 1);
        loadCurrent.run();
    });

    next.setOnClickListener(v -> {
        index[0] = Math.min(galleryItemsV2.size() - 1, index[0] + 1);
        loadCurrent.run();
    });

    zoomMinus.setOnClickListener(v -> {
        zoom[0] = Math.max(0.5f, zoom[0] - 0.25f);
        preview.setScaleX(zoom[0]);
        preview.setScaleY(zoom[0]);
    });

    zoomPlus.setOnClickListener(v -> {
        zoom[0] = Math.min(4.0f, zoom[0] + 0.25f);
        preview.setScaleX(zoom[0]);
        preview.setScaleY(zoom[0]);
    });

    reset.setOnClickListener(v -> {
        zoom[0] = 1.0f;
        preview.setScaleX(1.0f);
        preview.setScaleY(1.0f);
    });

    preview.setOnClickListener(v -> {
        zoom[0] = zoom[0] < 1.5f ? 2.0f : 1.0f;
        preview.setScaleX(zoom[0]);
        preview.setScaleY(zoom[0]);
    });

    download.setOnClickListener(v -> downloadToAndroidDownloadsV2(galleryItemsV2.get(index[0])));
    share.setOnClickListener(v -> shareNative(galleryItemsV2.get(index[0])));

    delete.setOnClickListener(v -> {
        JSONObject item = galleryItemsV2.get(index[0]);

        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage(item.optString("name", ""))
                .setPositiveButton("Delete", (d, w) -> {
                    dialog.dismiss();
                    deleteFile(item);
                })
                .setNegativeButton("Cancel", null)
                .show();
    });

    close.setOnClickListener(v -> dialog.dismiss());

    dialog.setContentView(box);
    dialog.show();

    Window w = dialog.getWindow();
    if (w != null) {
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    loadCurrent.run();
}

private void loadPreviewIntoV2(JSONObject item, ImageView preview, ProgressBar progress, TextView fileName) {
    preview.setImageDrawable(null);
    progress.setIndeterminate(true);
    progress.setProgress(0);
    fileName.setText("Loading preview...");

    new Thread(() -> {
        try {
            File f = downloadFileWithProgressV2(item, "family-cloud-preview", (done, total) -> {
                runOnUiThread(() -> {
                    if (total > 0) {
                        progress.setIndeterminate(false);
                        int pct = (int) Math.min(100, Math.max(0, (done * 100) / total));
                        progress.setProgress(pct);
                        fileName.setText("Loading preview " + pct + "%");
                    } else {
                        progress.setIndeterminate(true);
                    }
                });
            });

            Bitmap bm = decodeSampledBitmapV2(f.getAbsolutePath(), 2200, 2200);

            runOnUiThread(() -> {
                progress.setIndeterminate(false);
                progress.setProgress(100);

                if (bm != null) {
                    preview.setImageBitmap(bm);
                    fileName.setText(item.optString("name", ""));
                } else {
                    preview.setImageResource(android.R.drawable.ic_menu_report_image);
                    fileName.setText("Preview failed: " + item.optString("name", ""));
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                progress.setIndeterminate(false);
                progress.setProgress(0);
                preview.setImageResource(android.R.drawable.ic_menu_report_image);
                fileName.setText("Preview failed: " + e.getMessage());
            });
        }
    }).start();
}

private Bitmap decodeSampledBitmapV2(String path, int reqW, int reqH) {
    try {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);

        int sample = 1;
        while ((opts.outHeight / sample) > reqH || (opts.outWidth / sample) > reqW) {
            sample *= 2;
        }

        BitmapFactory.Options opts2 = new BitmapFactory.Options();
        opts2.inSampleSize = sample;
        opts2.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeFile(path, opts2);
    } catch (Exception e) {
        return null;
    }
}

private String safeCacheNameV2(JSONObject item) {
    String name = item.optString("name", "family-cloud-file").replaceAll("[\\\\/:*?\"<>|]", "_");
    return Math.abs(galleryKeyV2(item).hashCode()) + "-" + name;
}

private File downloadFileWithProgressV2(JSONObject item, String cacheFolder, GalleryProgressV2 cb) throws Exception {
    File dir = new File(getCacheDir(), cacheFolder);
    if (!dir.exists()) dir.mkdirs();

    File out = new File(dir, safeCacheNameV2(item));

    if (out.exists() && out.length() > 0) {
        if (cb != null) cb.onProgress(out.length(), out.length());
        return out;
    }

    URL u = new URL(fileUrl(item));
    HttpURLConnection c = (HttpURLConnection) u.openConnection();
    c.setRequestProperty("X-FC-Token", token);
    c.setConnectTimeout(15000);
    c.setReadTimeout(300000);

    long total = c.getContentLengthLong();
    int code = c.getResponseCode();

    if (code < 200 || code >= 300) {
        throw new Exception("server " + code);
    }

    long done = 0;

    try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
        byte[] buf = new byte[128 * 1024];
        int n;

        while ((n = in.read(buf)) != -1) {
            fos.write(buf, 0, n);
            done += n;
            if (cb != null) cb.onProgress(done, total);
        }
    }

    out.setLastModified(System.currentTimeMillis());
    return out;
}

private void downloadToAndroidDownloadsV2(JSONObject item) {
    try {
        String name = item.optString("name", "family-cloud-file").replaceAll("[\\\\/:*?\"<>|]", "_");

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(fileUrl(item)));
        req.addRequestHeader("X-FC-Token", token);
        req.setTitle(name);
        req.setDescription("Downloading from Family Cloud");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(req);

        toast("Download started");
    } catch (Exception e) {
        toast("Download failed: " + e.getMessage());
    }
}

private void downloadSelectedGalleryFilesV2() {
    if (selectedGalleryKeysV2.isEmpty()) {
        toast("No files selected");
        return;
    }

    for (JSONObject item : galleryItemsV2) {
        if (selectedGalleryKeysV2.contains(galleryKeyV2(item))) {
            downloadToAndroidDownloadsV2(item);
        }
    }
}

private void confirmDeleteSelectedGalleryFilesV2() {
    if (selectedGalleryKeysV2.isEmpty()) {
        toast("No files selected");
        return;
    }

    new AlertDialog.Builder(this)
            .setTitle("Delete selected files?")
            .setMessage("Selected files: " + selectedGalleryKeysV2.size())
            .setPositiveButton("Delete", (d, w) -> deleteSelectedGalleryFilesV2())
            .setNegativeButton("Cancel", null)
            .show();
}

private void deleteSelectedGalleryFilesV2() {
    new Thread(() -> {
        int deleted = 0;

        for (JSONObject item : galleryItemsV2) {
            if (!selectedGalleryKeysV2.contains(galleryKeyV2(item))) continue;

            try {
                JSONObject b = new JSONObject();
                b.put("folder", item.optString("folder"));
                b.put("name", item.optString("name"));

                JSONObject r = post("/api/native/delete", b, true);
                if (r.optBoolean("ok")) deleted++;
            } catch (Exception ignored) {}
        }

        int finalDeleted = deleted;

        runOnUiThread(() -> {
            toast("Deleted " + finalDeleted + " files");
            selectedGalleryKeysV2.clear();
            gallerySelectModeV2 = false;
            showGallery(currentPage, currentType);
        });
    }).start();
}

// FC_NATIVE_GALLERY_FINAL_END

private void showUpload() {
        baseScreen("Upload", true);
        content.addView(tv("Upload files to your current logged-in Family Cloud account. No manual email nonsense.", 15, WHITE, 0));
        Button pick = btn("Choose Files", GREEN);
        content.addView(pick, new LinearLayout.LayoutParams(-1, dp(60)));
        final TextView status = tv("Waiting for files...", 14, YELLOW, 0);
        content.addView(status, panelLp());

        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, PICK_UPLOAD);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_UPLOAD && result == RESULT_OK && data != null) {
            final ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());

            baseScreen("Uploading", true);
            final TextView status = tv("Starting upload...", 15, YELLOW, 1);
            content.addView(status, panelLp());

            runIo(new Job() {
                public void run() throws Exception {
                    int ok = 0, fail = 0;
                    for (int i = 0; i < uris.size(); i++) {
                        final int pos = i + 1;
                        runOnUiThread(new Runnable() { public void run() { status.setText("Uploading " + pos + " / " + uris.size()); }});
                        try { uploadOne(uris.get(i)); ok++; } catch (Exception e) { fail++; }
                    }
                    final int fok = ok, ffail = fail;
                    runOnUiThread(new Runnable() { public void run() { status.setText("Finished. OK: " + fok + " Failed: " + ffail); }});
                }
            });
        }
    }

    private String getName(Uri uri) {
        String name = "upload.bin";
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
            c.close();
        }
        return name == null ? "upload.bin" : name;
    }

    private void uploadOne(Uri uri) throws Exception {
        String boundary = "FC" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection)new URL(url("/api/upload")).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (cookie != null && cookie.length() > 0) c.setRequestProperty("Cookie", cookie);

        String name = getName(uri);
        OutputStream os = c.getOutputStream();
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + name.replace("\"", "_") + "\"\r\n").getBytes());
        os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());

        InputStream is = getContentResolver().openInputStream(uri);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        is.close();

        os.write(("\r\n--" + boundary + "--\r\n").getBytes());
        os.close();

        int code = c.getResponseCode();
        if (code >= 400) throw new IOException(readStream(c.getErrorStream()));
    }

    private void showAccount() {
    base("Account");
    nav();

    root.addView(text("Email: " + email, 18, Color.YELLOW));

    Button zip = button("Create ZIP Backup");
    zip.setOnClickListener(v -> startZipProcess());

    Button changePassword = button("Change Password");
    changePassword.setOnClickListener(v -> showChangePasswordDialog());

    Button deleteData = button("Delete My Whole Data");
    deleteData.setOnClickListener(v -> {
        final EditText confirm = input("Type DELETE", "");
        new AlertDialog.Builder(this)
                .setTitle("Delete all uploaded data?")
                .setMessage("This deletes your whole server data.")
                .setView(confirm)
                .setPositiveButton("Delete", (d, w) -> {
                    if ("DELETE".equals(confirm.getText().toString().trim())) deleteMyData();
                    else toast("Wrong confirmation");
                })
                .setNegativeButton("Cancel", null)
                .show();
    });

    root.addView(zip);
    root.addView(changePassword);
    root.addView(deleteData);
}

    



private void deleteMyData() {
    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("confirm", "DELETE");

            JSONObject r = post("/api/native/account/delete-data", b, true);
            if (!r.optBoolean("ok")) {
                throw new Exception(r.optString("error", "delete failed"));
            }

            toast("All server data deleted");
            runOnUiThread(this::showDashboard);
        } catch (Exception e) {
            toast("Delete data failed: " + e.getMessage());
        }
    }).start();
}

private void showAdminLock() {
    Dialog dialog = new Dialog(this);

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(24, 24, 24, 24);
    box.setBackgroundColor(Color.rgb(15, 15, 15));

    TextView title = text("Admin Locked", 24, Color.YELLOW);
    TextView sub = text("Enter admin code to open native admin panel.", 14, Color.LTGRAY);

    EditText code = input("Admin code", adminCode.length() > 0 ? adminCode : "");
    code.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

    Button unlock = button("Unlock Admin");
    unlock.setOnClickListener(v -> {
        String c = code.getText().toString().trim();

        if (c.length() < 4) {
            toast("Enter admin code");
            return;
        }

        adminCode = c;
        prefs.edit().putString("adminCode", adminCode).apply();

        dialog.dismiss();
        showAdmin();
    });

    box.addView(title);
    box.addView(sub);
    box.addView(code);
    box.addView(unlock);

    dialog.setContentView(box);
    dialog.show();

    Window w = dialog.getWindow();
    if (w != null) {
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

private void showAdmin() {
    base("Admin");
    nav();
    root.addView(text("Loading admin panel...", 16, Color.YELLOW));

    new Thread(() -> {
        try {
            String q = "?code=" + URLEncoder.encode(adminCode.length() > 0 ? adminCode : ADMIN_CODE, "UTF-8");
            JSONObject ru = get("/api/native/admin/users" + q);
            JSONObject rd = get("/api/native/admin/drives" + q);

            JSONArray users = ru.optJSONArray("users");
            JSONArray drives = rd.optJSONArray("drives");

            if (users == null) users = new JSONArray();
            if (drives == null) drives = new JSONArray();

            JSONArray finalUsers = users;
            JSONArray finalDrives = drives;

            runOnUiThread(() -> {
                base("Admin");
                nav();

                root.addView(text("Drives", 22, Color.YELLOW));
                for (int i = 0; i < finalDrives.length(); i++) {
                    JSONObject d = finalDrives.optJSONObject(i);
                    if (d != null) addAdminDrive(d);
                }

                root.addView(text("Users", 22, Color.YELLOW));
                for (int i = 0; i < finalUsers.length(); i++) {
                    JSONObject u = finalUsers.optJSONObject(i);
                    if (u != null) addAdminUser(u, finalDrives);
                }
            });
        } catch (Exception e) {
            toast("Admin error: " + e.getMessage());
        }
    }).start();
}

    private void loadAdminPanel() {
        baseScreen("Admin Panel", true);
        final TextView out = tv("Loading admin data...", 13, WHITE, 0);
        content.addView(out, panelLp());

        runIo(new Job() {
            public void run() throws Exception {
                final JSONObject dash = safeGet("/api/admin/dashboard");
                final JSONObject drives = safeGet("/api/admin/drives");
                final JSONObject users = safeGet("/api/admin/users");

                runOnUiThread(new Runnable() {
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Total Users: ").append(q(dash, "totalUsers")).append("\n");
                        sb.append("Real User Used: ").append(q(dash, "realUserUsed")).append("\n");
                        sb.append("Total Storage Given: ").append(q(dash, "totalStorageGiven")).append("\n");
                        sb.append("Real Files: ").append(q(dash, "realFiles")).append("\n");
                        sb.append("Drive Count: ").append(q(dash, "driveCount")).append("\n\n");

                        sb.append("Physical Drives:\n");
                        JSONArray d = drives.optJSONArray("drives");
                        if (d != null) for (int i = 0; i < d.length(); i++) {
                            JSONObject x = d.optJSONObject(i);
                            if (x == null) continue;
                            sb.append("- ").append(q(x, "name")).append(" · ").append(q(x, "system")).append(" · Temp: ").append(q(x, "temperature")).append(" · Free: ").append(q(x, "free")).append("\n");
                        }

                        sb.append("\nUsers:\n");
                        JSONArray u = users.optJSONArray("users");
                        if (u != null) for (int i = 0; i < u.length(); i++) {
                            JSONObject x = u.optJSONObject(i);
                            if (x == null) continue;
                            sb.append("- ").append(q(x, "email")).append(" · ").append(q(x, "role")).append(" · ").append(q(x, "drive", "driveName")).append(" · ").append(q(x, "used")).append(" / ").append(q(x, "storage")).append("\n");
                        }

                        out.setText(sb.toString());
                        addAdminCreateUser(drives);
                    }
                });
            }
        });
    }

    private void addAdminCreateUser(JSONObject drivesData) {
        content.addView(tv("Add New User", 24, YELLOW, 1));
        final EditText email = input("user@example.com", false);
        final EditText pass = input("Temporary password", false);
        final EditText storage = input("Storage, example: 50GB", false);

        final Spinner driveSpin = new Spinner(this);
        final Spinner roleSpin = new Spinner(this);

        ArrayList<String> driveNames = new ArrayList<>();
        JSONArray ds = drivesData.optJSONArray("drives");
        if (ds != null) for (int i = 0; i < ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            if (d != null) driveNames.add(q(d, "name", "driveName"));
        }
        if (driveNames.isEmpty()) driveNames.add("SERVER");

        driveSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, driveNames));
        roleSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"User", "Admin"}));

        Button create = btn("Create User", GREEN);
        content.addView(email);
        content.addView(pass);
        content.addView(storage);
        content.addView(driveSpin);
        content.addView(roleSpin);
        content.addView(create);

        create.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runIo(new Job() {
                    public void run() throws Exception {
                        JSONObject b = new JSONObject();
                        b.put("email", email.getText().toString().trim());
                        b.put("password", pass.getText().toString());
                        b.put("storage", storage.getText().toString().trim());
                        b.put("drive", String.valueOf(driveSpin.getSelectedItem()));
                        b.put("role", String.valueOf(roleSpin.getSelectedItem()));
                        postJson("/api/admin/add-user", b);
                        runOnUiThread(new Runnable() { public void run() { toast("User created"); loadAdminPanel(); }});
                    }
                });
            }
        });
    }

    @Override public void onBackPressed() {
        showDashboard();
    }

private void showModePicker() {
    base("Choose Server");

    root.addView(text("Choose where Family Cloud should connect.", 18, Color.WHITE));
    root.addView(text("Local starts empty. Native server is prefilled with your server URL.", 14, Color.LTGRAY));

    Button local = button("Local server");
    local.setText("Local server\nEnter local IP manually");
    local.setOnClickListener(v -> showLogin("", "Local"));

    Button nativeServer = button("Native server");
    nativeServer.setText("Native server\n" + ONLINE_URL);
    nativeServer.setOnClickListener(v -> showLogin(ONLINE_URL, "Native"));

    root.addView(local);
    root.addView(nativeServer);

    if (baseUrl != null && baseUrl.length() > 8) {
        Button saved = button("Use saved server");
        saved.setText("Use saved server\n" + baseUrl);
        saved.setOnClickListener(v -> showLogin(baseUrl, "Saved"));
        root.addView(saved);
    }
}

private void showLogin(String suggestedUrl, String mode) {
    base(mode + " Login");

    EditText server = input(mode.equals("Local") ? "Local URL, example http://192.168.1.5:3000" : "Server URL", suggestedUrl);
    EditText em = input("User ID / Email", email);
    EditText pass = input("Password", "");
    pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

    Button login = button("Login");
    login.setOnClickListener(v -> {
        baseUrl = cleanUrl(server.getText().toString().trim());
        email = em.getText().toString().trim().toLowerCase(Locale.ROOT);

        if (baseUrl.length() < 8) {
            toast("Enter server URL first");
            return;
        }

        doLogin(email, pass.getText().toString());
    });

    Button back = button("Back");
    back.setOnClickListener(v -> showModePicker());

    root.addView(text("Session is saved in app storage after successful login.", 14, Color.LTGRAY));
    root.addView(server);
    root.addView(em);
    root.addView(pass);
    root.addView(login);
    root.addView(back);
}

}
