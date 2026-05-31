package com.familycloud.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

public class GalleryActivity extends Activity {
    private static final int PAGE_LIMIT = 35;
    private static final long CACHE_LIMIT = 750L * 1024L * 1024L;
    private static final long CACHE_TRIM_TARGET = 350L * 1024L * 1024L;

    private LinearLayout root;
    private GridLayout grid;
    private TextView status;
    private TextView pageText;
    private ProgressBar loadingBar;
    private Button floatingSelect;
    private LinearLayout selectBar;

    private String baseUrl = "";
    private String token = "";

    private int page = 1;
    private int totalPages = 1;
    private String type = "all";

    private final ArrayList<GalleryItem> items = new ArrayList<>();
    private final HashSet<String> selected = new HashSet<>();

    private boolean selectMode = false;
    private File cacheRoot;

    private FrameLayout previewOverlay;
    private FrameLayout previewStage;
    private TextView previewTitle;
    private int previewIndex = -1;
    private float zoom = 1f;
    private float lastX = 0f;
    private float downX = 0f;
    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        baseUrl = cleanUrl(prefs.getString(MainActivity.KEY_BASE_URL, ""));
        token = prefs.getString(MainActivity.KEY_TOKEN, "");

        cacheRoot = new File(getCacheDir(), "sri_ladli_gallery_cache");
        if (!cacheRoot.exists()) cacheRoot.mkdirs();

        buildPage();
        loadPage(1, "all");
    }

    private void buildPage() {
        FrameLayout frame = new FrameLayout(this);

        LinearLayout pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setBackgroundColor(Color.rgb(4, 4, 4));

        HorizontalScrollView topScroll = new HorizontalScrollView(this);
        topScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(4));
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button all = topButton("All");
        Button photos = topButton("Photo Only");
        Button videos = topButton("Video Only");
        Button prev = topButton("Previous Page");
        Button next = topButton("Next Page");
        Button back = topButton("Back");

        all.setOnClickListener(v -> loadPage(1, "all"));
        photos.setOnClickListener(v -> loadPage(1, "photo"));
        videos.setOnClickListener(v -> loadPage(1, "video"));
        prev.setOnClickListener(v -> {
            if (page > 1) loadPage(page - 1, type);
        });
        next.setOnClickListener(v -> {
            if (page < totalPages) loadPage(page + 1, type);
        });
        back.setOnClickListener(v -> finish());

        loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setMax(100);
        loadingBar.setProgress(0);

        pageText = text("Page 1", 13, Color.LTGRAY);

        top.addView(all);
        top.addView(photos);
        top.addView(videos);
        top.addView(prev);
        top.addView(next);
        top.addView(pageText);
        top.addView(back);

        topScroll.addView(top);
        pageRoot.addView(topScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageRoot.addView(loadingBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)));

        status = text("Loading gallery...", 14, Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        pageRoot.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setPadding(0, 0, 0, dp(42));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(58));
        root.setBackgroundColor(Color.rgb(4, 4, 4));

        grid = new GridLayout(this);
        grid.setColumnCount(3);
        root.addView(grid);

        scroll.addView(root);
        pageRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        frame.addView(pageRoot);

        selectBar = new LinearLayout(this);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setPadding(dp(8), dp(8), dp(8), dp(20));
        selectBar.setGravity(Gravity.CENTER);
        selectBar.setBackgroundColor(Color.rgb(12, 12, 12));
        selectBar.setVisibility(View.GONE);

        Button share = topButton("Share");
        Button download = topButton("Download");
        Button delete = topButton("Delete");
        Button cancel = topButton("Cancel");

        share.setOnClickListener(v -> shareSelected());
        download.setOnClickListener(v -> downloadSelected());
        delete.setOnClickListener(v -> deleteSelected());
        cancel.setOnClickListener(v -> {
            selectMode = false;
            selected.clear();
            refreshCards();
        });

        selectBar.addView(share);
        selectBar.addView(download);
        selectBar.addView(delete);
        selectBar.addView(cancel);

        FrameLayout.LayoutParams selectLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectLp.gravity = Gravity.BOTTOM;
        frame.addView(selectBar, selectLp);

        floatingSelect = topButton("Select");
        floatingSelect.setText("Select");
        floatingSelect.setOnClickListener(v -> {
            selectMode = true;
            refreshCards();
        });

        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(112), dp(56));
        fabLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        fabLp.setMargins(0, 0, dp(16), dp(58));
        frame.addView(floatingSelect, fabLp);

        buildPreview(frame);
        setContentView(frame);
    }

    private void buildPreview(FrameLayout frame) {
        previewOverlay = new FrameLayout(this);
        previewOverlay.setBackgroundColor(Color.BLACK);
        previewOverlay.setVisibility(View.GONE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(4));

        Button close = topButton("Close");
        close.setOnClickListener(v -> closePreview());

        Button del = topButton("Delete");
        del.setBackground(bg(Color.rgb(210, 35, 35), Color.rgb(230, 60, 60)));
        del.setTextColor(Color.WHITE);
        del.setOnClickListener(v -> deleteCurrentPreview());

        previewTitle = text("Preview", 14, Color.WHITE);
        previewTitle.setSingleLine(true);

        top.addView(close);
        top.addView(previewTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(del);

        previewStage = new FrameLayout(this);
        previewStage.setBackgroundColor(Color.BLACK);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(8), dp(6), dp(8), dp(26));

        Button zoomOut = topButton("Zoom -");
        Button reset = topButton("Reset");
        Button zoomIn = topButton("Zoom +");
        Button share = topButton("Share");
        Button download = topButton("Download");

        zoomOut.setOnClickListener(v -> setZoom(Math.max(1f, zoom - 0.35f)));
        reset.setOnClickListener(v -> setZoom(1f));
        zoomIn.setOnClickListener(v -> setZoom(Math.min(5f, zoom + 0.35f)));
        share.setOnClickListener(v -> shareOne(currentItem()));
        download.setOnClickListener(v -> downloadOne(currentItem()));

        bottom.addView(zoomOut);
        bottom.addView(reset);
        bottom.addView(zoomIn);
        bottom.addView(share);
        bottom.addView(download);

        container.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(previewStage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        container.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        previewOverlay.addView(container);
        frame.addView(previewOverlay);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                setZoom(Math.max(1f, Math.min(5f, zoom * detector.getScaleFactor())));
                return true;
            }
        });

        previewStage.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);

            if (ev.getPointerCount() > 1) return true;

            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                downX = ev.getX();
                lastX = ev.getX();
                return true;
            }

            if (ev.getAction() == MotionEvent.ACTION_UP) {
                float dx = ev.getX() - downX;

                if (Math.abs(dx) > dp(70)) {
                    if (dx < 0) previewNext();
                    else previewPrev();
                }

                return true;
            }

            return true;
        });
    }

    private void loadPage(int newPage, String newType) {
        page = Math.max(1, newPage);
        type = newType == null ? "all" : newType;

        loadingBar.setProgress(4);
        status.setText("Loading page " + page + "...");
        grid.removeAllViews();
        items.clear();

        new Thread(() -> {
            try {
                JSONObject data = get("/api/native/files?page=" + page + "&limit=" + PAGE_LIMIT + "&type=" + enc(type));
                JSONArray arr = data.optJSONArray("items");
                if (arr == null) arr = data.optJSONArray("files");

                totalPages = Math.max(1, data.optInt("pages", data.optInt("totalPages", 1)));

                ArrayList<GalleryItem> loaded = new ArrayList<>();

                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        GalleryItem item = new GalleryItem();
                        item.name = o.optString("name", o.optString("filename", "file"));
                        item.folder = o.optString("folder", o.optString("path", ""));
                        item.size = o.optLong("size", 0);
                        item.mime = o.optString("mime", o.optString("mimetype", guessMime(item.name)));
                        item.kind = detectKind(item.name, item.mime, o.optString("kind", o.optString("type", "")));
                        item.key = item.folder + "/" + item.name;
                        loaded.add(item);
                    }
                }

                runOnUiThread(() -> {
                    items.clear();
                    items.addAll(loaded);
                    loadingBar.setProgress(45);
                    renderGrid();
                });

                trimCacheIfNeeded();

                runOnUiThread(() -> {
                    loadingBar.setProgress(100);
                    status.setText("Loaded " + items.size() + " files · cache: current, previous, next page");
                    pageText.setText("Page " + page + " / " + totalPages);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingBar.setProgress(0);
                    status.setText("Gallery load failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void renderGrid() {
        grid.removeAllViews();

        int screen = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(7);
        int cell = (screen - dp(16) - gap * 4) / 3;

        for (int i = 0; i < items.size(); i++) {
            GalleryItem item = items.get(i);
            View card = buildCard(item, i, cell);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cell;
            lp.height = cell + dp(32);
            lp.setMargins(gap, gap, gap, gap);
            grid.addView(card, lp);
        }

        refreshCards();
    }

    private View buildCard(GalleryItem item, int index, int cell) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        card.setBackground(bg(Color.rgb(18, 18, 18), Color.rgb(40, 40, 40)));
        card.setTag(item.key);

        FrameLayout thumbWrap = new FrameLayout(this);
        thumbWrap.setBackgroundColor(Color.rgb(10, 10, 10));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbWrap.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = text(item.kind.equals("video") ? "▶" : "", 30, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        thumbWrap.addView(badge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView check = text("✓", 19, Color.rgb(255, 196, 0));
        check.setTypeface(Typeface.DEFAULT_BOLD);
        check.setGravity(Gravity.CENTER);
        check.setBackground(bg(Color.rgb(0, 0, 0), Color.rgb(255, 196, 0)));
        check.setVisibility(selected.contains(item.key) ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(30), dp(30));
        cp.gravity = Gravity.TOP | Gravity.RIGHT;
        cp.setMargins(0, dp(5), dp(5), 0);
        thumbWrap.addView(check, cp);

        TextView name = text(item.name, 11, Color.LTGRAY);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);

        card.addView(thumbWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell));
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        card.setOnClickListener(v -> {
            if (selectMode) {
                toggleSelect(item.key);
            } else {
                openPreview(index);
            }
        });

        card.setOnLongClickListener(v -> {
            selectMode = true;
            toggleSelect(item.key);
            return true;
        });

        loadThumb(item, img);
        return card;
    }

    private void loadThumb(GalleryItem item, ImageView img) {
    if (item.kind.equals("video")) {
        img.setBackgroundColor(Color.rgb(20, 20, 20));
        return;
    }

    new Thread(() -> {
        try {
            Bitmap bm = decodeRemoteSampled(item, 360);
            if (bm != null) runOnUiThread(() -> img.setImageBitmap(bm));
        } catch (Exception ignored) {}
    }).start();
}



    private void refreshCards() {
        selectBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        floatingSelect.setVisibility(selectMode ? View.GONE : View.VISIBLE);

        for (int i = 0; i < grid.getChildCount(); i++) {
            View v = grid.getChildAt(i);
            String key = String.valueOf(v.getTag());
            v.setBackground(bg(
                    selected.contains(key) ? Color.rgb(45, 35, 0) : Color.rgb(18, 18, 18),
                    selected.contains(key) ? Color.rgb(255, 196, 0) : Color.rgb(40, 40, 40)
            ));
        }

        status.setText(selectMode ? selected.size() + " selected" : "Page " + page + " · " + items.size() + " files");
    }

    private void toggleSelect(String key) {
        if (selected.contains(key)) selected.remove(key);
        else selected.add(key);
        refreshCards();
    }

    private void openPreview(int index) {
        if (index < 0 || index >= items.size()) return;
        previewIndex = index;
        previewOverlay.setVisibility(View.VISIBLE);
        showPreviewItem();
    }

    private void closePreview() {
        previewOverlay.setVisibility(View.GONE);
        previewStage.removeAllViews();
        zoom = 1f;
    }

    private GalleryItem currentItem() {
        if (previewIndex < 0 || previewIndex >= items.size()) return null;
        return items.get(previewIndex);
    }

    private void showPreviewItem() {
    GalleryItem item = currentItem();
    if (item == null) return;

    previewStage.removeAllViews();
    previewTitle.setText(item.name);
    zoom = 1f;

    TextView loading = text("Loading preview...", 16, Color.LTGRAY);
    loading.setGravity(Gravity.CENTER);
    previewStage.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    if (item.kind.equals("video")) {
        runOnUiThread(() -> {
            try {
                previewStage.removeAllViews();

                VideoView vv = new VideoView(this);
                vv.setVideoURI(Uri.parse(fileUrl(item)));
                vv.setOnPreparedListener(mp -> {
                    mp.setLooping(false);
                    vv.start();
                });

                vv.setOnErrorListener((mp, what, extra) -> {
                    previewStage.removeAllViews();
                    TextView err = text("Video preview not supported by Android codec.\nUse Download or Share.", 15, Color.rgb(255, 120, 120));
                    err.setGravity(Gravity.CENTER);
                    previewStage.addView(err, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return true;
                });

                previewStage.addView(vv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Exception e) {
                previewStage.removeAllViews();
                TextView err = text("Video preview failed: " + e.getMessage(), 15, Color.rgb(255, 120, 120));
                err.setGravity(Gravity.CENTER);
                previewStage.addView(err, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
        return;
    }

    new Thread(() -> {
        try {
            Bitmap bm = decodeRemoteSampled(item, 1800);

            runOnUiThread(() -> {
                previewStage.removeAllViews();

                if (bm == null) {
                    TextView err = text("Image preview failed. Use Download or Share.", 15, Color.rgb(255, 120, 120));
                    err.setGravity(Gravity.CENTER);
                    previewStage.addView(err, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    return;
                }

                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageBitmap(bm);
                iv.setTag("previewImage");
                previewStage.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                previewStage.removeAllViews();
                TextView err = text("Preview failed: " + e.getMessage(), 15, Color.rgb(255, 120, 120));
                err.setGravity(Gravity.CENTER);
                previewStage.addView(err, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            });
        }
    }).start();
}



    private void setZoom(float z) {
        zoom = z;
        View v = previewStage.findViewWithTag("previewImage");
        if (v != null) {
            v.setScaleX(zoom);
            v.setScaleY(zoom);
        }
    }

    private void previewNext() {
        if (previewIndex < items.size() - 1) {
            previewIndex++;
            showPreviewItem();
            return;
        }

        if (page < totalPages) {
            int targetPage = page + 1;
            loadPage(targetPage, type);
            new android.os.Handler().postDelayed(() -> {
                if (!items.isEmpty()) {
                    previewIndex = 0;
                    previewOverlay.setVisibility(View.VISIBLE);
                    showPreviewItem();
                }
            }, 1300);
        }
    }

    private void previewPrev() {
        if (previewIndex > 0) {
            previewIndex--;
            showPreviewItem();
            return;
        }

        if (page > 1) {
            int targetPage = page - 1;
            loadPage(targetPage, type);
            new android.os.Handler().postDelayed(() -> {
                if (!items.isEmpty()) {
                    previewIndex = items.size() - 1;
                    previewOverlay.setVisibility(View.VISIBLE);
                    showPreviewItem();
                }
            }, 1300);
        }
    }

    private void deleteCurrentPreview() {
        GalleryItem item = currentItem();
        if (item == null) return;

        new Thread(() -> {
            boolean ok = deleteServer(item);
            if (!ok) {
                runOnUiThread(() -> toast("Delete failed"));
                return;
            }

            runOnUiThread(() -> {
                selected.remove(item.key);
                int old = previewIndex;
                items.remove(item);
                renderGrid();

                if (items.isEmpty()) {
                    closePreview();
                    return;
                }

                previewIndex = Math.min(old, items.size() - 1);
                previewOverlay.setVisibility(View.VISIBLE);
                showPreviewItem();
            });
        }).start();
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            toast("No files selected");
            return;
        }

        ArrayList<GalleryItem> toDelete = new ArrayList<>();
        for (GalleryItem item : items) {
            if (selected.contains(item.key)) toDelete.add(item);
        }

        new Thread(() -> {
            for (GalleryItem item : toDelete) deleteServer(item);

            runOnUiThread(() -> {
                items.removeAll(toDelete);
                selected.clear();
                selectMode = false;
                renderGrid();
                toast("Deleted selected files");
            });
        }).start();
    }

    private boolean deleteServer(GalleryItem item) {
        try {
            JSONObject body = new JSONObject();
            body.put("folder", item.folder);
            body.put("name", item.name);

            HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/api/native/delete").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(120000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-FC-Token", token);

            try (FileOutputStream ignored = null) {}

            try (java.io.OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes("UTF-8"));
            }

            int code = c.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private void shareSelected() {
        ArrayList<Uri> uris = new ArrayList<>();

        for (GalleryItem item : items) {
            if (selected.contains(item.key)) {
                Uri u = cachedUri(item);
                if (u != null) uris.add(u);
            }
        }

        if (uris.isEmpty()) {
            toast("No cached selected files to share yet");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share files"));
    }

    private void shareOne(GalleryItem item) {
        Uri u = cachedUri(item);
        if (u == null) {
            toast("File not cached yet");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(item.mime == null ? "*/*" : item.mime);
        intent.putExtra(Intent.EXTRA_STREAM, u);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share file"));
    }

    private Uri cachedUri(GalleryItem item) {
        try {
            File f = ensureCachedForShare(item);
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        } catch (Exception e) {
            return null;
        }
    }

    private void downloadSelected() {
        for (GalleryItem item : items) {
            if (selected.contains(item.key)) downloadOne(item);
        }
        toast("Downloads started");
    }

    private void downloadOne(GalleryItem item) {
        if (item == null) return;

        try {
            String url = fileUrl(item);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.addRequestHeader("X-FC-Token", token);
            req.setTitle(item.name);
            req.setDescription("Downloading from SRI LADLI");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            String dir = item.kind.equals("video") ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
            req.setDestinationInExternalPublicDir(dir, "SRI-LADLI/" + item.name);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(req);
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private void prefetchPages(int current) {
        ArrayList<Integer> keep = new ArrayList<>();
        if (current > 1) keep.add(current - 1);
        keep.add(current);
        if (current < totalPages) keep.add(current + 1);

        cleanupOldPageDirs(keep);

        for (int p : keep) {
            new Thread(() -> {
                try {
                    JSONObject data = get("/api/native/files?page=" + p + "&limit=" + PAGE_LIMIT + "&type=" + enc(type));
                    JSONArray arr = data.optJSONArray("items");
                    if (arr == null) arr = data.optJSONArray("files");
                    if (arr == null) return;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        GalleryItem item = new GalleryItem();
                        item.name = o.optString("name", o.optString("filename", "file"));
                        item.folder = o.optString("folder", o.optString("path", ""));
                        item.size = o.optLong("size", 0);
                        item.mime = o.optString("mime", o.optString("mimetype", guessMime(item.name)));
                        item.kind = detectKind(item.name, item.mime, o.optString("kind", o.optString("type", "")));
                        item.key = item.folder + "/" + item.name;
                        ensureCached(item);
                    }
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void cleanupOldPageDirs(ArrayList<Integer> keep) {
        try {
            File[] dirs = cacheRoot.listFiles();
            if (dirs == null) return;

            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                String n = d.getName();
                if (!n.startsWith("page_")) continue;

                int num = Integer.parseInt(n.substring(5));
                if (!keep.contains(num)) deleteFileTree(d);
            }
        } catch (Exception ignored) {}
    }

    private File ensureCached(GalleryItem item) throws Exception {
        File dir = new File(cacheRoot, "page_" + page);
        if (!dir.exists()) dir.mkdirs();

        File out = new File(dir, safeFile(item.key));
        if (out.exists() && out.length() > 0) {
            out.setLastModified(System.currentTimeMillis());
            return out;
        }

        HttpURLConnection c = (HttpURLConnection) new URL(fileUrl(item)).openConnection();
        c.setRequestProperty("X-FC-Token", token);
        c.setConnectTimeout(15000);
        c.setReadTimeout(600000);

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

        try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }

        out.setLastModified(System.currentTimeMillis());
        return out;
    }

    
private Bitmap decodeRemoteSampled(GalleryItem item, int req) throws Exception {
    HttpURLConnection c1 = (HttpURLConnection) new URL(fileUrl(item)).openConnection();
    c1.setRequestProperty("X-FC-Token", token);
    c1.setConnectTimeout(15000);
    c1.setReadTimeout(180000);

    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;

    try (InputStream in = c1.getInputStream()) {
        BitmapFactory.decodeStream(in, null, bounds);
    }

    int sample = 1;
    while (bounds.outWidth / sample > req || bounds.outHeight / sample > req) {
        sample *= 2;
    }

    HttpURLConnection c2 = (HttpURLConnection) new URL(fileUrl(item)).openConnection();
    c2.setRequestProperty("X-FC-Token", token);
    c2.setConnectTimeout(15000);
    c2.setReadTimeout(180000);

    BitmapFactory.Options real = new BitmapFactory.Options();
    real.inSampleSize = Math.max(1, sample);
    real.inPreferredConfig = Bitmap.Config.RGB_565;

    try (InputStream in = c2.getInputStream()) {
        return BitmapFactory.decodeStream(in, null, real);
    }
}

private File ensureCachedForShare(GalleryItem item) throws Exception {
    File dir = new File(cacheRoot, "share_cache");
    if (!dir.exists()) dir.mkdirs();

    File out = new File(dir, safeFile(item.key));
    if (out.exists() && out.length() > 0) {
        out.setLastModified(System.currentTimeMillis());
        return out;
    }

    HttpURLConnection c = (HttpURLConnection) new URL(fileUrl(item)).openConnection();
    c.setRequestProperty("X-FC-Token", token);
    c.setConnectTimeout(15000);
    c.setReadTimeout(600000);

    int code = c.getResponseCode();
    if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

    try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
    }

    out.setLastModified(System.currentTimeMillis());
    trimCacheIfNeeded();
    return out;
}

private void trimCacheIfNeeded() {
        long size = folderSize(cacheRoot);
        if (size < CACHE_LIMIT) return;

        ArrayList<File> files = new ArrayList<>();
        collectFiles(cacheRoot, files);
        Collections.sort(files, Comparator.comparingLong(File::lastModified));

        long deleted = 0;
        for (File f : files) {
            long len = f.length();
            if (f.delete()) deleted += len;
            if (deleted >= CACHE_TRIM_TARGET) break;
        }
    }

    private long folderSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += folderSize(k);
        return total;
    }

    private void collectFiles(File f, ArrayList<File> out) {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            out.add(f);
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) collectFiles(k, out);
    }

    private void deleteFileTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteFileTree(k);
        }
        f.delete();
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

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        return out.toString("UTF-8");
    }

    private String fileUrl(GalleryItem item) throws Exception {
        return baseUrl + "/api/native/file?folder=" + enc(item.folder) + "&name=" + enc(item.name) + "&token=" + enc(token);
    }

    private String enc(String s) throws Exception {
        return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private String cleanUrl(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String detectKind(String name, String mime, String kind) {
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (k.contains("video") || m.startsWith("video/") || n.matches(".*\\.(mp4|mov|mkv|avi|webm|3gp|m4v|mpeg|mpg|ts|wmv|flv|mts|m2ts|divx|ogv)$")) return "video";
        if (k.contains("photo") || k.contains("image") || m.startsWith("image/") || n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|tif|tiff|avif|ico)$")) return "photo";
        return "file";
    }

    private String guessMime(String name) {
        String ext = "";
        int dot = name == null ? -1 : name.lastIndexOf(".");
        if (dot >= 0 && dot < name.length() - 1) ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    private Bitmap decodeSampled(File file, int req) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);

        int sample = 1;
        while (o.outWidth / sample > req || o.outHeight / sample > req) sample *= 2;

        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), real);
    }

    private String safeFile(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private TextView text(String v, int size, int color) {
        TextView t = new TextView(this);
        t.setText(v == null ? "" : v);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(5), dp(4), dp(5), dp(4));
        return t;
    }

    private Button topButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(Color.BLACK);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(42));
        b.setBackground(bg(Color.rgb(255, 196, 0), Color.rgb(255, 196, 0)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);

        return b;
    }

    private GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static class GalleryItem {
        String name;
        String folder;
        String mime;
        String kind;
        String key;
        long size;
    }
}
