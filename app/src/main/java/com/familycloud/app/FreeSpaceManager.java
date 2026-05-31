package com.familycloud.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class FreeSpaceManager extends Activity {
    private static final int DELETE_REQUEST = 9401;
    private static final int CHECK_BATCH = 200;

    private LinearLayout root;
    private TextView status;
    private ProgressBar progress;

    private String baseUrl = "";
    private String token = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        baseUrl = cleanUrl(prefs.getString(MainActivity.KEY_BASE_URL, ""));
        token = prefs.getString(MainActivity.KEY_TOKEN, "");

        buildUi();
        startCheck();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.rgb(4, 4, 4));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(90));
        root.setBackgroundColor(Color.rgb(4, 4, 4));

        TextView title = text("Free Up Space", 28, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        LinearLayout card = card();

        status = text("Checking phone files against server...", 16, Color.WHITE);
        status.setGravity(Gravity.CENTER);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);

        Button cancel = ghost("Cancel");
        cancel.setOnClickListener(v -> finish());

        card.addView(status, full());
        card.addView(progress, full());
        card.addView(cancel, full());

        root.addView(title, full());
        root.addView(card, full());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void startCheck() {
        new Thread(() -> {
            try {
                ArrayList<LocalMedia> all = new ArrayList<>();
                all.addAll(scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "photo"));
                all.addAll(scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video"));

                if (all.isEmpty()) {
                    ui(100, "No local photos/videos found.");
                    return;
                }

                HashSet<String> verified = askVerified(all);

                ArrayList<Uri> deleteUris = new ArrayList<>();
                for (LocalMedia m : all) {
                    if (verified.contains(m.key)) deleteUris.add(m.uri);
                }

                runOnUiThread(() -> requestDelete(deleteUris));
            } catch (Exception e) {
                ui(0, "Free up failed: " + e.getMessage());
            }
        }).start();
    }

    private ArrayList<LocalMedia> scan(Uri collection, String kind) {
        ArrayList<LocalMedia> result = new ArrayList<>();

        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE
        };

        try (Cursor c = getContentResolver().query(collection, projection, null, null, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (c == null) return result;

            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            int mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String name = safe(c.getString(nameCol), "file");
                long size = c.getLong(sizeCol);
                long modified = c.getLong(modCol);
                String mime = safe(c.getString(mimeCol), guessMime(name));

                if (size <= 0) continue;

                LocalMedia m = new LocalMedia();
                m.uri = android.content.ContentUris.withAppendedId(collection, id);
                m.kind = kind;
                m.name = name;
                m.size = size;
                m.modified = modified;
                m.mime = mime;
                m.key = kind + "|Phone|" + name + "|" + size + "|" + modified;

                result.add(m);
            }
        } catch (Exception ignored) {}

        return result;
    }

    private HashSet<String> askVerified(ArrayList<LocalMedia> all) throws Exception {
        HashSet<String> verified = new HashSet<>();

        for (int start = 0; start < all.size(); start += CHECK_BATCH) {
            int end = Math.min(start + CHECK_BATCH, all.size());
            JSONArray files = new JSONArray();

            for (int i = start; i < end; i++) {
                LocalMedia m = all.get(i);
                JSONObject o = new JSONObject();
                o.put("key", m.key);
                o.put("name", m.name);
                o.put("size", m.size);
                o.put("modified", m.modified);
                o.put("mime", m.mime);
                o.put("kind", m.kind);
                files.put(o);
            }

            JSONObject body = new JSONObject();
            body.put("files", files);

            JSONObject r = postJson(baseUrl + "/api/native/sync/verified", body);
            JSONArray arr = r.optJSONArray("verified");

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) verified.add(arr.getString(i));
            }

            int pct = (int) Math.min(100, (end * 100.0) / Math.max(1, all.size()));
            ui(pct, "Checking backed up files: " + end + "/" + all.size());
        }

        return verified;
    }

    private void requestDelete(ArrayList<Uri> uris) {
        if (uris.isEmpty()) {
            ui(100, "No safe backed-up local files found to delete.");
            return;
        }

        try {
            status.setText("Android will ask permission to delete " + uris.size() + " files.");

            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), DELETE_REQUEST, null, 0, 0, 0);
            } else {
                int deleted = 0;
                for (Uri u : uris) deleted += getContentResolver().delete(u, null, null);
                toastSafe("Deleted " + deleted + " local backed-up files.");
                finish();
            }
        } catch (Exception e) {
            ui(0, "Delete request failed: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == DELETE_REQUEST) {
            if (res == RESULT_OK) toastSafe("Free up complete.");
            else toastSafe("Free up cancelled.");
            finish();
        }
    }

    private JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-FC-Token", token);

        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes("UTF-8"));
        }

        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());

        if (code < 200 || code >= 300) throw new Exception(text);

        return new JSONObject(text);
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        return out.toString("UTF-8");
    }

    private void ui(int pct, String msg) {
        runOnUiThread(() -> {
            progress.setProgress(Math.max(0, Math.min(100, pct)));
            status.setText(msg);
        });
    }

    private String cleanUrl(String url) {
        url = url == null ? "" : url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String safe(String v, String fallback) {
        if (v == null || v.trim().isEmpty()) return fallback;
        return v.trim();
    }

    private String guessMime(String name) {
        String ext = "";
        int dot = name == null ? -1 : name.lastIndexOf(".");
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(bg(Color.rgb(16, 16, 16), Color.rgb(80, 62, 0)));
        return box;
    }

    private TextView text(String v, int size, int color) {
        TextView t = new TextView(this);
        t.setText(v == null ? "" : v);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(6), dp(5), dp(6), dp(5));
        return t;
    }

    private Button ghost(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinHeight(dp(50));
        b.setBackground(bg(Color.rgb(30, 30, 30), Color.rgb(75, 75, 75)));
        return b;
    }

    private GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(7), dp(4), dp(7));
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toastSafe(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    private static class LocalMedia {
        Uri uri;
        String key;
        String name;
        String mime;
        String kind;
        long size;
        long modified;
    }
}
