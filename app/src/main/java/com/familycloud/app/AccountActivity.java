package com.familycloud.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class AccountActivity extends Activity {
    private LinearLayout root;
    private String baseUrl = "";
    private String token = "";
    private String email = "";

    private TextView zipStatus;
    private ProgressBar zipProgress;
    private Button zipDownloadButton;
    private String activeZipJobId = "";

    private TextView directStatus;
    private ProgressBar directProgress;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        baseUrl = cleanUrl(prefs.getString(MainActivity.KEY_BASE_URL, ""));
        token = prefs.getString(MainActivity.KEY_TOKEN, "");
        email = prefs.getString(MainActivity.KEY_EMAIL, "");

        showAccount();
    }

    private void showAccount() {
        base();

        TextView title = text("Account", 28, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());

        TextView logged = text("Logged in as: " + email, 15, Color.LTGRAY);
        logged.setGravity(Gravity.CENTER);
        root.addView(logged, full());

        zipSection();
        directDownloadSection();
        passwordSection();
        deleteDataSection();

        Button back = ghost("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back, full());
    }

    private void zipSection() {
        LinearLayout card = cardBox();
        card.addView(section("1. Create ZIP Request"));

        card.addView(text("ZIP parts are max 80 GB. If your data is larger, Part 1 is available for 24 hours. After it expires, the server creates the next part.", 14, Color.LTGRAY), full());

        zipStatus = text("No ZIP requested yet.", 15, Color.WHITE);
        zipProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        zipProgress.setMax(100);

        Button create = primary("Create ZIP Request");
        create.setOnClickListener(v -> startZipRequest());

        zipDownloadButton = primary("Download Ready ZIP Part");
        zipDownloadButton.setVisibility(Button.GONE);

        card.addView(create, full());
        card.addView(zipProgress, full());
        card.addView(zipStatus, full());
        card.addView(zipDownloadButton, full());

        root.addView(card, full());
    }

    private void startZipRequest() {
        zipProgress.setProgress(0);
        zipDownloadButton.setVisibility(Button.GONE);
        zipStatus.setText("Requesting ZIP job...");

        new Thread(() -> {
            try {
                JSONObject r = post("/api/native/account/zip-start", new JSONObject());

                activeZipJobId = r.optString("id", "");
                if (activeZipJobId.length() < 1) throw new Exception("server returned no ZIP job id");

                runOnUiThread(() -> {
                    String msg = r.optString("message", "");
                    if (msg.length() > 0) zipStatus.setText(msg);
                });

                pollZipStatus(activeZipJobId);
            } catch (Exception e) {
                runOnUiThread(() -> zipStatus.setText("ZIP request failed: " + e.getMessage()));
            }
        }).start();
    }

    private void pollZipStatus(String jobId) {
        new Thread(() -> {
            while (true) {
                try {
                    JSONObject r = get("/api/native/account/zip-status/" + enc(jobId));

                    JSONObject part = r.optJSONObject("activePart");
                    int progress = r.optInt("progress", 0);
                    String status = part == null ? r.optString("status") : part.optString("status");
                    String eta = r.optString("etaText", "");
                    String message = r.optString("message", "");
                    String expires = r.optString("expiresText", "");
                    String downloadUrl = r.optString("downloadUrl", "");

                    int partNum = part == null ? 0 : part.optInt("partNumber");
                    int totalParts = r.optInt("totalParts", 1);

                    runOnUiThread(() -> {
                        zipProgress.setProgress(Math.max(0, Math.min(100, progress)));

                        String text = "Status: " + status
                                + "\nPart: " + partNum + " / " + totalParts
                                + "\nProgress: " + progress + "%"
                                + (eta.length() > 0 ? "\nETA: " + eta : "")
                                + (expires.length() > 0 ? "\nAvailable until: " + expires : "")
                                + (message.length() > 0 ? "\n\n" + message : "");

                        zipStatus.setText(text);
                    });

                    if ("ready".equalsIgnoreCase(status) && downloadUrl.length() > 0) {
                        runOnUiThread(() -> {
                            zipDownloadButton.setVisibility(Button.VISIBLE);
                            zipDownloadButton.setOnClickListener(v -> downloadWithManager(absoluteUrl(downloadUrl), "sri-ladli-backup-part.zip", true));
                        });
                    }

                    if ("finished".equalsIgnoreCase(r.optString("status")) || "failed".equalsIgnoreCase(status)) {
                        return;
                    }

                    Thread.sleep(3000);
                } catch (Exception e) {
                    runOnUiThread(() -> zipStatus.setText("ZIP status failed: " + e.getMessage()));
                    return;
                }
            }
        }).start();
    }

    private void directDownloadSection() {
        LinearLayout card = cardBox();
        card.addView(section("2. Download Directly To Gallery"));

        card.addView(text("Downloads photos/videos one by one, not ZIP. Shows completed count and ETA.", 14, Color.LTGRAY), full());

        directStatus = text("No direct download running.", 15, Color.WHITE);
        directProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        directProgress.setMax(100);

        Button download = primary("Download All Photos/Videos To Gallery");
        download.setOnClickListener(v -> startDirectDownload());

        card.addView(download, full());
        card.addView(directProgress, full());
        card.addView(directStatus, full());

        root.addView(card, full());
    }

    private void startDirectDownload() {
        directProgress.setProgress(0);
        directStatus.setText("Loading server media list...");

        new Thread(() -> {
            try {
                JSONArray all = new JSONArray();

                loadFilesInto(all, "photo");
                loadFilesInto(all, "video");

                int total = all.length();
                if (total == 0) {
                    runOnUiThread(() -> directStatus.setText("No photos/videos found on server."));
                    return;
                }

                long started = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {
                    JSONObject item = all.getJSONObject(i);

                    String name = item.optString("name", "file");
                    String folder = item.optString("folder", "");
                    String url = baseUrl + "/api/native/file?folder=" + enc(folder) + "&name=" + enc(name) + "&token=" + enc(token);

                    boolean isVideo = name.toLowerCase().matches(".*\\.(mp4|mov|mkv|avi|webm|3gp|m4v)$");
                    String dest = isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;

                    downloadWithManager(url, "SRI-LADLI/" + name, false, dest);

                    int done = i + 1;
                    int pct = (int) ((done * 100.0) / total);
                    long elapsed = Math.max(1, (System.currentTimeMillis() - started) / 1000);
                    long eta = done > 0 ? Math.round((elapsed / (double) done) * (total - done)) : 0;

                    runOnUiThread(() -> {
                        directProgress.setProgress(pct);
                        directStatus.setText("Queued downloads: " + done + " / " + total + "\nETA to queue remaining: " + eta + " sec");
                    });

                    Thread.sleep(120);
                }

                runOnUiThread(() -> directStatus.setText("All downloads queued. Android Download Manager will finish them."));
            } catch (Exception e) {
                runOnUiThread(() -> directStatus.setText("Direct download failed: " + e.getMessage()));
            }
        }).start();
    }

    private void loadFilesInto(JSONArray out, String type) throws Exception {
        int page = 1;

        while (true) {
            JSONObject r = get("/api/native/files?page=" + page + "&limit=100&type=" + enc(type));
            JSONArray arr = r.optJSONArray("items");

            if (arr == null || arr.length() == 0) return;

            for (int i = 0; i < arr.length(); i++) out.put(arr.getJSONObject(i));

            int pages = r.optInt("pages", page);
            if (page >= pages) return;
            page++;
        }
    }

    private void passwordSection() {
        LinearLayout card = cardBox();
        card.addView(section("3. Change Password"));

        EditText oldPass = input("Current password", "");
        EditText newPass = input("New password", "");
        EditText confirm = input("Confirm new password", "");

        oldPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button change = primary("Change Password");
        change.setOnClickListener(v -> {
            String oldP = oldPass.getText().toString();
            String newP = newPass.getText().toString();
            String conf = confirm.getText().toString();

            if (!newP.equals(conf)) {
                toast("New passwords do not match");
                return;
            }

            if (newP.length() < 1) {
                toast("Enter new password");
                return;
            }

            new Thread(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("oldPassword", oldP);
                    b.put("newPassword", newP);

                    JSONObject r = post("/api/native/account/change-password", b);
                    if (!r.optBoolean("ok", true)) throw new Exception(r.optString("error", "failed"));

                    runOnUiThread(() -> toast("Password changed"));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Password change failed: " + e.getMessage()));
                }
            }).start();
        });

        card.addView(oldPass, full());
        card.addView(newPass, full());
        card.addView(confirm, full());
        card.addView(change, full());

        root.addView(card, full());
    }

    private void deleteDataSection() {
        LinearLayout card = cardBox();
        card.addView(section("4. Delete All Data"));

        card.addView(text("This deletes all your server files. Type DELETE to confirm.", 14, Color.rgb(255, 120, 120)), full());

        EditText confirm = input("Type DELETE", "");

        Button delete = danger("Delete All My Data");
        delete.setOnClickListener(v -> {
            if (!"DELETE".equals(confirm.getText().toString().trim())) {
                toast("Type DELETE first");
                return;
            }

            new Thread(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("confirm", "DELETE");

                    JSONObject r = post("/api/native/account/delete-data", b);
                    if (!r.optBoolean("ok", true)) throw new Exception(r.optString("error", "failed"));

                    runOnUiThread(() -> toast("All server data deleted"));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Delete failed: " + e.getMessage()));
                }
            }).start();
        });

        card.addView(confirm, full());
        card.addView(delete, full());

        root.addView(card, full());
    }

    private JSONObject get(String endpoint) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
        c.setRequestProperty("X-FC-Token", token);
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);

        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code < 200 || code >= 300) throw new Exception(text);
        return new JSONObject(text);
    }

    private JSONObject post(String endpoint, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
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

    private void downloadWithManager(String url, String filename, boolean zip) {
        downloadWithManager(url, filename, zip, zip ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_PICTURES);
    }

    private void downloadWithManager(String url, String filename, boolean zip, String dir) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(filename);
            req.setDescription("Downloading from SRI LADLI");
            req.addRequestHeader("X-FC-Token", token);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(dir, filename);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);

            if (zip) toast("ZIP download started");
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private String absoluteUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return baseUrl + url;
        return baseUrl + "/" + url;
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private String cleanUrl(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
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

        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(bg(Color.rgb(16, 16, 16), Color.rgb(80, 62, 0)));
        return box;
    }

    private TextView section(String s) {
        TextView t = text(s, 21, Color.rgb(255, 196, 0));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView text(String v, int size, int color) {
        TextView t = new TextView(this);
        t.setText(v == null ? "" : v);
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
        e.setBackground(bg(Color.rgb(24, 24, 24), Color.rgb(70, 70, 70)));
        return e;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(52));
        b.setBackground(bg(Color.rgb(255, 196, 0), Color.rgb(255, 196, 0)));
        return b;
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

    private Button danger(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(50));
        b.setBackground(bg(Color.rgb(210, 35, 35), Color.rgb(230, 60, 60)));
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
