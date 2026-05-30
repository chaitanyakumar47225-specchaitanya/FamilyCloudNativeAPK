package com.familycloud.app;

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
    private static final String PREFS = "family_cloud_native";
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
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        io = Executors.newFixedThreadPool(4);
        baseUrl = prefs.getString("baseUrl", DEFAULT_URL);
        cookie = prefs.getString("cookie", "");
        showModeScreen();
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
                prefs.edit().putString("baseUrl", baseUrl).apply();
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
        baseScreen("Login", false);
        final EditText email = input("Email", false);
        final EditText pass = input("Password", true);
        email.setText(prefs.getString("email", ""));
        content.addView(email);
        content.addView(pass);

        Button login = btn("Login", GREEN);
        Button urlBtn = btn("Change Server URL", TOP);
        content.addView(login, new LinearLayout.LayoutParams(-1, dp(58)));
        content.addView(urlBtn, new LinearLayout.LayoutParams(-1, dp(58)));

        login.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String em = email.getText().toString().trim();
                final String pw = pass.getText().toString();
                if (!em.contains("@") || pw.length() < 1) {
                    alert("Missing", "Enter email and password.");
                    return;
                }

                runIo(new Job() {
                    public void run() throws Exception {
                        boolean ok = false;
                        try {
                            JSONObject body = new JSONObject();
                            body.put("email", em);
                            body.put("password", pw);
                            postJson("/api/login", body);
                            ok = true;
                        } catch (Exception ignored) {}

                        if (!ok) {
                            String form = "email=" + enc(em) + "&password=" + enc(pw);
                            request("POST", "/login", form, "application/x-www-form-urlencoded");
                        }

                        currentEmail = em;
                        prefs.edit()
                                .putString("email", em)
                                .putString("baseUrl", baseUrl)
                                .putString("cookie", cookie == null ? "" : cookie)
                                .apply();

                        runOnUiThread(new Runnable() { public void run() { showDashboard(); }});
                    }
                });
            }
        });

        urlBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showEditUrl(); }});
    }

    private void logout() {
        prefs.edit().remove("cookie").apply();
        cookie = "";
        currentEmail = "";
        showModeScreen();
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
        baseScreen("Dashboard", true);
        content.addView(tv("Family Cloud storage, backup, gallery, and device health.", 15, WHITE, 0));

        final TextView user = tv("Logged in as: loading...", 15, YELLOW, 1);
        content.addView(user, panelLp());

        final TextView storage = tv("Storage Used\nLoading...", 22, WHITE, 1);
        storage.setGravity(Gravity.CENTER);
        content.addView(storage, panelLp());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        final TextView files = tv("📁 Files\n0", 20, WHITE, 1);
        final TextView photos = tv("🖼 Photos\n0", 20, WHITE, 1);
        final TextView videos = tv("🎥 Videos\n0", 20, WHITE, 1);
        final TextView temp = tv("🌡 Temperature\nLoading...", 20, WHITE, 1);
        grid.addView(card(files));
        grid.addView(card(photos));
        grid.addView(card(videos));
        grid.addView(card(temp));
        content.addView(grid);

        TextView qa = tv("Quick Actions", 26, YELLOW, 1);
        content.addView(qa);
        Button g = btn("Gallery", TOP);
        Button u = btn("Upload Files", TOP);
        Button a = btn("Account", TOP);
        Button ad = btn("Admin", TOP);
        content.addView(g);
        content.addView(u);
        content.addView(a);
        content.addView(ad);

        g.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showGallery(); }});
        u.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showUpload(); }});
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAccount(); }});
        ad.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAdmin(); }});

        runIo(new Job() {
            public void run() throws Exception {
                final JSONObject cu = safeGet("/api/current-user");
                final JSONObject d = safeGet("/api/dashboard");
                final JSONObject t = safeGet("/api/temperature");

                runOnUiThread(new Runnable() {
                    public void run() {
                        String em = q(cu, "email", "user", "username");
                        if (em.length() == 0 || em.equals("unknown")) em = prefs.getString("email", currentEmail);
                        currentEmail = em;
                        user.setText("Logged in as: " + em);

                        String used = q(d, "usedStorage", "realUserUsed", "storageUsed", "usedText");
                        String quota = q(d, "quota", "allowedStorage", "totalStorage", "quotaText");
                        int percent = qi(d, "storagePercent", "percent", "usedPercent");
                        storage.setText("Storage Used\n" + percent + "%\n" + used + (quota.length() > 0 ? " / " + quota : " used"));

                        files.setText("📁 Files\n" + q(d, "totalFiles", "realFiles", "fileCount"));
                        photos.setText("🖼 Photos\n" + q(d, "totalPhotos", "photos", "photoCount"));
                        videos.setText("🎥 Videos\n" + q(d, "totalVideos", "videos", "videoCount"));

                        String tempText = q(t, "displayText", "temperatureText");
                        String drive = q(t, "assignedDrive");
                        temp.setText("🌡 Temperature\n" + (tempText.length() > 0 ? tempText : "No sensor") + "\n" + drive);
                    }
                });
            }
        });
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

    private void showGallery() {
        baseScreen("Gallery", true);
        selected.clear();
        selectMode = false;
        allMedia.clear();
        filteredMedia.clear();
        page = 1;

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = btn("Refresh", GREEN);
        Button select = btn("Select", TOP);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"All", "Photos only", "Videos only"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);

        controls.addView(refresh, new LinearLayout.LayoutParams(0, dp(54), 1));
        controls.addView(select, new LinearLayout.LayoutParams(0, dp(54), 1));
        controls.addView(sp, new LinearLayout.LayoutParams(0, dp(54), 1));
        content.addView(controls);

        galleryStatus = tv("Loading...", 13, YELLOW, 0);
        content.addView(galleryStatus, panelLp());

        LinearLayout p1 = pagerBar();
        content.addView(p1);

        galleryGrid = new GridLayout(this);
        galleryGrid.setColumnCount(3);
        content.addView(galleryGrid);

        LinearLayout p2 = pagerBar();
        content.addView(p2);

        floatingSelectBar = new LinearLayout(this);
        floatingSelectBar.setOrientation(LinearLayout.HORIZONTAL);
        floatingSelectBar.setGravity(Gravity.CENTER);
        floatingSelectBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        floatingSelectBar.setBackgroundColor(TOP);
        root.addView(floatingSelectBar, new LinearLayout.LayoutParams(-1, -2));
        updateFloatingBar();

        refresh.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { loadGallery(); }});
        select.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectMode = !selectMode;
                if (!selectMode) selected.clear();
                renderGallery();
            }
        });
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                filter = pos == 1 ? "photo" : pos == 2 ? "video" : "all";
                page = 1;
                applyGalleryFilter();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadGallery();
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
                .setPositiveButton("Delete", (d, w) -> runIo(new Job() {
                    public void run() throws Exception {
                        for (JSONObject f : items) deleteFileRequest(f);
                        selected.clear();
                        runOnUiThread(new Runnable() { public void run() { loadGallery(); }});
                    }
                }))
                .show();
    }

    private void deleteOne(final JSONObject f) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage(fileName(f))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> runIo(new Job() {
                    public void run() throws Exception {
                        deleteFileRequest(f);
                        runOnUiThread(new Runnable() { public void run() { loadGallery(); }});
                    }
                }))
                .show();
    }

    private void deleteFileRequest(JSONObject f) throws Exception {
        JSONObject body = new JSONObject();
        body.put("path", q(f, "path", "name", "filename"));
        postJson("/api/delete-file", body);
    }

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
        baseScreen("Account", true);
        content.addView(tv("Logged in as:", 16, YELLOW, 1));
        content.addView(tv(currentEmail.length() > 0 ? currentEmail : prefs.getString("email", "unknown"), 16, WHITE, 0));
        Button dash = btn("Dashboard", TOP);
        Button logout = btn("Logout", RED);
        content.addView(dash);
        content.addView(logout);
        dash.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showDashboard(); }});
        logout.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { logout(); }});
    }

    private void showAdmin() {
        baseScreen("Admin", true);
        final EditText code = input("Admin code", true);
        Button unlock = btn("Unlock Admin", GREEN);
        content.addView(code);
        content.addView(unlock);

        unlock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runIo(new Job() {
                    public void run() throws Exception {
                        JSONObject b = new JSONObject();
                        b.put("code", code.getText().toString());
                        postJson("/api/admin/unlock", b);
                        runOnUiThread(new Runnable() { public void run() { loadAdminPanel(); }});
                    }
                });
            }
        });
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
}
