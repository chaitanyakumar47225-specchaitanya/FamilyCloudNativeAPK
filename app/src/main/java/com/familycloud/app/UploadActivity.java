package com.familycloud.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class UploadActivity extends Activity {
    private static final int PICK_MEDIA_FILES = 6101;

    private LinearLayout root;
    private TextView status;
    private TextView selectedText;
    private ProgressBar progress;

    private String baseUrl = "";
    private String token = "";

    private final ArrayList<UploadItem> selected = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        baseUrl = cleanUrl(prefs.getString(MainActivity.KEY_BASE_URL, ""));
        token = prefs.getString(MainActivity.KEY_TOKEN, "");

        showUploadPage();
    }

    private void showUploadPage() {
        base();

        TextView title = text("Upload Files", 28, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());

        LinearLayout card = cardBox();

        TextView note = text("Only photo and video files are supported.", 15, Color.LTGRAY);
        note.setGravity(Gravity.CENTER);

        selectedText = text("No files selected.", 15, Color.WHITE);
        selectedText.setGravity(Gravity.CENTER);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);

        status = text("Choose photo/video files to upload.", 15, Color.LTGRAY);
        status.setGravity(Gravity.CENTER);

        Button choose = primary("Select Photos / Videos");
        choose.setOnClickListener(v -> openPicker());

        Button upload = primary("Upload Selected Files");
        upload.setOnClickListener(v -> uploadSelected());

        Button clear = ghost("Clear Selection");
        clear.setOnClickListener(v -> {
            selected.clear();
            progress.setProgress(0);
            selectedText.setText("No files selected.");
            status.setText("Selection cleared.");
        });

        Button back = ghost("Back");
        back.setOnClickListener(v -> finish());

        card.addView(note, full());
        card.addView(choose, full());
        card.addView(upload, full());
        card.addView(clear, full());
        card.addView(progress, full());
        card.addView(selectedText, full());
        card.addView(status, full());
        card.addView(back, full());

        root.addView(card, full());
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/*",
                "video/*"
        });
        startActivityForResult(Intent.createChooser(i, "Select photos/videos"), PICK_MEDIA_FILES);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req != PICK_MEDIA_FILES || res != RESULT_OK || data == null) return;

        selected.clear();

        ClipData clip = data.getClipData();

        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                addUri(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            addUri(data.getData());
        }

        long total = 0;
        for (UploadItem item : selected) total += item.size;

        selectedText.setText("Selected: " + selected.size() + " supported files\nTotal: " + fmt(total));

        if (selected.isEmpty()) {
            status.setText("No supported photo/video selected.");
        } else {
            status.setText("Ready to upload.");
        }
    }

    private void addUri(Uri uri) {
        try {
            String name = getDisplayName(uri);
            long size = getSize(uri);
            String mime = getContentResolver().getType(uri);

            if (mime == null || mime.trim().isEmpty()) {
                mime = guessMime(name);
            }

            if (!isSupported(name, mime)) {
                return;
            }

            UploadItem item = new UploadItem();
            item.uri = uri;
            item.name = cleanFileName(name);
            item.size = Math.max(0, size);
            item.mime = mime;
            item.kind = mime.toLowerCase(Locale.ROOT).startsWith("video/") ? "video" : "photo";
            item.key = item.kind + "|ManualUpload|" + item.name + "|" + item.size + "|" + System.currentTimeMillis();

            selected.add(item);
        } catch (Exception ignored) {}
    }

    private boolean isSupported(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        if (m.startsWith("image/")) return true;
        if (m.startsWith("video/")) return true;

        return n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|mp4|mov|mkv|avi|webm|3gp|m4v)$");
    }

    private void uploadSelected() {
        if (baseUrl.length() < 8 || token.length() < 8) {
            toast("Login/session missing");
            return;
        }

        if (selected.isEmpty()) {
            toast("Select photo/video files first");
            return;
        }

        progress.setProgress(0);
        status.setText("Starting upload...");

        new Thread(() -> {
            long totalBytes = 0;
            for (UploadItem item : selected) totalBytes += item.size;

            final long finalTotalBytes = totalBytes;
            long[] uploadedBytes = new long[]{0};
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < selected.size(); i++) {
                UploadItem item = selected.get(i);
                int fileIndex = i + 1;

                try {
                    uploadOne(item, doneForFile -> {
                        long currentTotal = uploadedBytes[0] + doneForFile;
                        int pct = finalTotalBytes > 0 ? (int) Math.min(100, (currentTotal * 100) / finalTotalBytes) : 0;

                        long elapsed = Math.max(1, (System.currentTimeMillis() - startTime) / 1000);
                        long eta = currentTotal > 0
                                ? Math.round((elapsed / (double) currentTotal) * (finalTotalBytes - currentTotal))
                                : 0;

                        runOnUiThread(() -> {
                            progress.setProgress(pct);
                            status.setText(
                                    "Uploading: " + fileIndex + " / " + selected.size()
                                            + "\nFile: " + item.name
                                            + "\nCompleted: " + pct + "%"
                                            + "\nUploaded: " + fmt(currentTotal) + " / " + fmt(finalTotalBytes)
                                            + "\nETA: " + etaText(eta)
                            );
                        });
                    });

                    uploadedBytes[0] += item.size;
                } catch (Exception e) {
                    String msg = e.getMessage();
                    runOnUiThread(() -> status.setText("Upload failed on " + item.name + "\n" + msg));
                    return;
                }
            }

            runOnUiThread(() -> {
                progress.setProgress(100);
                status.setText("Upload complete: " + selected.size() + " files uploaded.");
                toast("Upload complete");
            });
        }).start();
    }

    private void uploadOne(UploadItem item, ProgressCallback cb) throws Exception {
        String boundary = "SRI-LADLI-UPLOAD-" + UUID.randomUUID();

        URL url = new URL(baseUrl + "/api/native/sync/upload");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();

        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setChunkedStreamingMode(128 * 1024);
        c.setConnectTimeout(15000);
        c.setReadTimeout(600000);
        c.setRequestProperty("X-FC-Token", token);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = c.getOutputStream()) {
            writeField(out, boundary, "key", item.key);
            writeField(out, boundary, "name", item.name);
            writeField(out, boundary, "relativePath", "ManualUpload");
            writeField(out, boundary, "size", String.valueOf(item.size));
            writeField(out, boundary, "modified", String.valueOf(System.currentTimeMillis() / 1000));
            writeField(out, boundary, "mime", item.mime);
            writeField(out, boundary, "kind", item.kind);

            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + item.name.replace("\"", "_") + "\"\r\n");
            write(out, "Content-Type: " + item.mime + "\r\n\r\n");

            try (InputStream in = getContentResolver().openInputStream(item.uri)) {
                if (in == null) throw new Exception("Cannot open selected file");

                byte[] buf = new byte[128 * 1024];
                int n;
                long sent = 0;

                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    sent += n;
                    cb.onProgress(sent);
                }
            }

            write(out, "\r\n--" + boundary + "--\r\n");
        }

        int code = c.getResponseCode();
        String response = read(code >= 400 ? c.getErrorStream() : c.getInputStream());

        if (code < 200 || code >= 300) {
            throw new Exception(response);
        }

        JSONObject r = new JSONObject(response);
        if (!r.optBoolean("ok", true)) {
            throw new Exception(r.optString("error", "Upload failed"));
        }
    }

    private void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, value == null ? "" : value);
        write(out, "\r\n");
    }

    private void write(OutputStream out, String s) throws Exception {
        out.write(s.getBytes("UTF-8"));
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;

        while ((n = in.read(b)) != -1) {
            out.write(b, 0, n);
        }

        return out.toString("UTF-8");
    }

    private String getDisplayName(Uri uri) {
        String result = null;

        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = c.getString(idx);
            }
        } catch (Exception ignored) {}

        if (result == null || result.trim().isEmpty()) {
            result = "upload-" + System.currentTimeMillis();
        }

        return result;
    }

    private long getSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return c.getLong(idx);
            }
        } catch (Exception ignored) {}

        return 0;
    }

    private String guessMime(String name) {
        String ext = "";
        int dot = name == null ? -1 : name.lastIndexOf(".");
        if (dot >= 0 && dot < name.length() - 1) {
            ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    private String cleanFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "upload-" + System.currentTimeMillis();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String cleanUrl(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String fmt(long bytes) {
        double b = Math.max(0, bytes);
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;

        while (b >= 1024 && i < u.length - 1) {
            b /= 1024.0;
            i++;
        }

        return String.format(Locale.US, i == 0 ? "%.0f %s" : "%.2f %s", b, u[i]);
    }

    private String etaText(long seconds) {
        if (seconds <= 0) return "calculating...";
        if (seconds < 60) return seconds + " sec";
        long min = seconds / 60;
        long sec = seconds % 60;
        return min + " min " + sec + " sec";
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

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(54));
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

    private GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(4), dp(7), dp(4), dp(7));
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private interface ProgressCallback {
        void onProgress(long doneForFile);
    }

    private static class UploadItem {
        Uri uri;
        String name;
        String mime;
        String kind;
        String key;
        long size;
    }
}
