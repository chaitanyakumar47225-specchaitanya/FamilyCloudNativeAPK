package com.familycloud.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
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
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;

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

    private static final long CACHE_LIMIT = 6L * 1024L * 1024L * 1024L;
    private static final long CACHE_DELETE_WHEN_FULL = 3L * 1024L * 1024L * 1024L;
    private static final long MIN_FREE_SPACE_FOR_CACHE = 6L * 1024L * 1024L * 1024L;

    private LinearLayout root;
    private GridLayout grid;
    private TextView status;
    private TextView pageTextTop;
    private TextView pageTextBottom;
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
    private int thumbDone = 0;
    private int thumbTotal = 1;

    private File cacheRoot;

    private FrameLayout previewOverlay;
    private FrameLayout previewStage;
    private TextView previewTitle;
    private int previewIndex = -1;
    private float zoom = 1f;
    private float downX = 0f;
    private ScaleGestureDetector scaleDetector;
    private ExoPlayer activePlayer;

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
        loadPage(1, "all", null);
    }

    private void buildPage() {
        FrameLayout frame = new FrameLayout(this);

        LinearLayout pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setBackgroundColor(Color.rgb(4, 4, 4));

        pageRoot.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setMax(100);
        loadingBar.setProgress(0);
        pageRoot.addView(loadingBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));

        status = text("Loading gallery...", 14, Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        pageRoot.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setPadding(0, 0, 0, dp(92));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(92));
        root.setBackgroundColor(Color.rgb(4, 4, 4));

        grid = new GridLayout(this);
        grid.setColumnCount(3);
        root.addView(grid);

        root.addView(bottomPageBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        pageRoot.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        frame.addView(pageRoot);

        buildSelectBar(frame);
        buildFloatingSelect(frame);
        buildPreview(frame);

        setContentView(frame);
    }

    private View topBar() {
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

        pageTextTop = text("Page 1", 13, Color.LTGRAY);

        all.setOnClickListener(v -> loadPage(1, "all", null));
        photos.setOnClickListener(v -> loadPage(1, "photo", null));
        videos.setOnClickListener(v -> loadPage(1, "video", null));
        prev.setOnClickListener(v -> {
            if (page > 1) loadPage(page - 1, type, null);
        });
        next.setOnClickListener(v -> {
            if (page < totalPages) loadPage(page + 1, type, null);
        });
        back.setOnClickListener(v -> finish());

        top.addView(all);
        top.addView(photos);
        top.addView(videos);
        top.addView(prev);
        top.addView(next);
        top.addView(pageTextTop);
        top.addView(back);

        topScroll.addView(top);
        return topScroll;
    }

    private View bottomPageBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(8), dp(14), dp(8), dp(82));

        Button prev = topButton("Previous Page");
        Button next = topButton("Next Page");
        pageTextBottom = text("Page 1", 13, Color.LTGRAY);
        pageTextBottom.setGravity(Gravity.CENTER);

        prev.setOnClickListener(v -> {
            if (page > 1) loadPage(page - 1, type, null);
        });

        next.setOnClickListener(v -> {
            if (page < totalPages) loadPage(page + 1, type, null);
        });

        bottom.addView(prev);
        bottom.addView(pageTextBottom);
        bottom.addView(next);

        return bottom;
    }

    private void buildSelectBar(FrameLayout frame) {
        selectBar = new LinearLayout(this);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setPadding(dp(8), dp(8), dp(8), dp(36));
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
            renderGrid();
        });

        selectBar.addView(share);
        selectBar.addView(download);
        selectBar.addView(delete);
        selectBar.addView(cancel);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        frame.addView(selectBar, lp);
    }

    private void buildFloatingSelect(FrameLayout frame) {
        floatingSelect = topButton("Select");
        floatingSelect.setOnClickListener(v -> {
            selectMode = true;
            renderGrid();
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(112), dp(56));
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setMargins(0, 0, dp(16), dp(92));
        frame.addView(floatingSelect, lp);
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
        del.setTextColor(Color.WHITE);
        del.setBackground(bg(Color.rgb(210, 35, 35), Color.rgb(230, 60, 60)));
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
        bottom.setPadding(dp(8), dp(6), dp(8), dp(82));

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

    private void loadPage(int newPage, String newType, Runnable afterLoaded) {
        page = Math.max(1, newPage);
        type = newType == null ? "all" : newType;

        setProgress(2, "Loading page " + page + "...");
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
                        item.pageNumber = page;

                        loaded.add(item);
                    }
                }

                runOnUiThread(() -> {
                    items.clear();
                    items.addAll(loaded);
                    updatePageLabels();
                    renderGrid();
                });

                if (hasEnoughFreeSpaceForCache()) {
                    cachePageFiles(loaded, page, true);
                    prefetchNeighbourPages(page);
                    trimCacheIfNeeded();
                } else {
                    runOnUiThread(() -> setProgress(0, "Low storage. Need 6 GB free for gallery cache. Free: " + fmt(cacheRoot.getUsableSpace())));
                }

                runOnUiThread(() -> {
                    setProgress(100, "Loaded page " + page + " · " + items.size() + " files");
                    if (afterLoaded != null) afterLoaded.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setProgress(0, "Gallery load failed: " + e.getMessage()));
            }
        }).start();
    }

    private void updatePageLabels() {
        String text = "Page " + page + " / " + totalPages;
        if (pageTextTop != null) pageTextTop.setText(text);
        if (pageTextBottom != null) pageTextBottom.setText(text);
    }

    private void renderGrid() {
        grid.removeAllViews();

        selectBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        floatingSelect.setVisibility(selectMode ? View.GONE : View.VISIBLE);

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

        status.setText(selectMode ? selected.size() + " selected" : "Page " + page + " · " + items.size() + " files");
    }

    private View buildCard(GalleryItem item, int index, int cell) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        card.setTag(item.key);
        card.setBackground(bg(
                selected.contains(item.key) ? Color.rgb(45, 35, 0) : Color.rgb(18, 18, 18),
                selected.contains(item.key) ? Color.rgb(255, 196, 0) : Color.rgb(40, 40, 40)
        ));

        FrameLayout thumbWrap = new FrameLayout(this);
        thumbWrap.setBackgroundColor(Color.rgb(10, 10, 10));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbWrap.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = text(item.kind.equals("video") ? "▶" : "", 30, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        thumbWrap.addView(badge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (selected.contains(item.key)) {
            TextView check = text("✓", 19, Color.rgb(255, 196, 0));
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            check.setBackground(bg(Color.rgb(0, 0, 0), Color.rgb(255, 196, 0)));

            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(30), dp(30));
            cp.gravity = Gravity.TOP | Gravity.RIGHT;
            cp.setMargins(0, dp(5), dp(5), 0);
            thumbWrap.addView(check, cp);
        }

        TextView name = text(item.name, 11, Color.LTGRAY);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);

        card.addView(thumbWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell));
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        card.setOnClickListener(v -> {
            if (selectMode) toggleSelect(item.key);
            else openPreview(index);
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
    new Thread(() -> {
        try {
            Bitmap bm = downloadThumbBitmap(item);
            runOnUiThread(() -> {
                if (bm != null) img.setImageBitmap(bm);

                thumbDone++;
                int pct = 35 + (int) Math.min(45, (thumbDone * 45.0) / Math.max(1, thumbTotal));
                setProgress(pct, "Loading thumbnails: " + thumbDone + "/" + thumbTotal);
            });
        } catch (Exception ignored) {
            runOnUiThread(() -> {
                thumbDone++;
                int pct = 35 + (int) Math.min(45, (thumbDone * 45.0) / Math.max(1, thumbTotal));
                setProgress(pct, "Loading thumbnails: " + thumbDone + "/" + thumbTotal);
            });
        }
    }).start();
}



    private void toggleSelect(String key) {
        if (selected.contains(key)) selected.remove(key);
        else selected.add(key);
        renderGrid();
    }

    private void openPreview(int index) {
        if (index < 0 || index >= items.size()) return;
        previewIndex = index;
        previewOverlay.setVisibility(View.VISIBLE);
        showPreviewItem();
    }

    
private void releasePlayer() {
    try {
        if (activePlayer != null) {
            activePlayer.release();
            activePlayer = null;
        }
    } catch (Exception ignored) {}
}

private void closePreview() {
        releasePlayer();
        previewOverlay.setVisibility(View.GONE);
        previewStage.removeAllViews();
        zoom = 1f;
    }

    private GalleryItem currentItem() {
        if (previewIndex < 0 || previewIndex >= items.size()) return null;
        return items.get(previewIndex);
    }

    private void showPreviewItem() {
        releasePlayer();
        GalleryItem item = currentItem();
        if (item == null) return;

        previewStage.removeAllViews();
        previewTitle.setText(item.name);
        zoom = 1f;

        TextView loading = text("Preview loading: 0%", 16, Color.LTGRAY);
        loading.setGravity(Gravity.CENTER);
        previewStage.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setProgress(0, "Preview loading: 0%");

        new Thread(() -> {
            try {
                File f = ensureCached(item, item.pageNumber, pct -> runOnUiThread(() -> {
                    loading.setText("Preview loading: " + pct + "%");
                    setProgress(pct, "Preview loading: " + pct + "%");
                }));

                runOnUiThread(() -> {
                    previewStage.removeAllViews();

                    if (item.kind.equals("video")) {
                        PlayerView playerView = new PlayerView(this);
                        playerView.setUseController(true);

                        activePlayer = new ExoPlayer.Builder(this).build();
                        playerView.setPlayer(activePlayer);

                        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(f));
                        activePlayer.setMediaItem(mediaItem);
                        activePlayer.prepare();
                        activePlayer.play();

                        previewStage.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        setProgress(100, "Video preview ready");
                    } else {
                        ImageView iv = new ImageView(this);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        iv.setImageURI(Uri.fromFile(f));
                        iv.setTag("previewImage");
                        previewStage.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        setProgress(100, "Photo preview ready");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    previewStage.removeAllViews();
                    TextView err = text("Preview failed: " + e.getMessage(), 15, Color.rgb(255, 120, 120));
                    err.setGravity(Gravity.CENTER);
                    previewStage.addView(err, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    setProgress(0, "Preview failed");
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
            loadPage(page + 1, type, () -> {
                if (!items.isEmpty()) {
                    previewIndex = 0;
                    previewOverlay.setVisibility(View.VISIBLE);
                    showPreviewItem();
                }
            });
        }
    }

    private void previewPrev() {
        if (previewIndex > 0) {
            previewIndex--;
            showPreviewItem();
            return;
        }

        if (page > 1) {
            loadPage(page - 1, type, () -> {
                if (!items.isEmpty()) {
                    previewIndex = items.size() - 1;
                    previewOverlay.setVisibility(View.VISIBLE);
                    showPreviewItem();
                }
            });
        }
    }

    private void deleteCurrentPreview() {
        GalleryItem item = currentItem();
        if (item == null) return;

        new Thread(() -> {
            boolean ok = deleteServer(item);

            runOnUiThread(() -> {
                if (!ok) {
                    toast("Delete failed");
                    return;
                }

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
        new Thread(() -> {
            ArrayList<Uri> uris = new ArrayList<>();

            for (GalleryItem item : items) {
                if (selected.contains(item.key)) {
                    Uri u = cachedUri(item);
                    if (u != null) uris.add(u);
                }
            }

            runOnUiThread(() -> {
                if (uris.isEmpty()) {
                    toast("No cached selected files to share");
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("*/*");
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share files"));
            });
        }).start();
    }

    private void shareOne(GalleryItem item) {
        if (item == null) return;

        new Thread(() -> {
            Uri u = cachedUri(item);

            runOnUiThread(() -> {
                if (u == null) {
                    toast("Share failed");
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(item.mime == null ? "*/*" : item.mime);
                intent.putExtra(Intent.EXTRA_STREAM, u);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share file"));
            });
        }).start();
    }

    private Uri cachedUri(GalleryItem item) {
        try {
            File f = ensureCached(item, item.pageNumber, pct -> setProgress(pct, "Preparing share: " + pct + "%"));
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
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(fileUrl(item)));
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

    private void cachePageFiles(ArrayList<GalleryItem> list, int pageNumber, boolean foreground) {
        long total = 0;
        for (GalleryItem item : list) total += Math.max(0, item.size);

        final long finalTotal = total;
        long[] done = new long[]{0};
        int count = Math.max(1, list.size());

        for (int i = 0; i < list.size(); i++) {
            GalleryItem item = list.get(i);
            int idx = i + 1;

            try {
                ensureCached(item, pageNumber, pctForFile -> {
                    if (!foreground) return;

                    long current = done[0] + Math.max(0, (item.size * pctForFile) / 100);
                    int pct;

                    if (finalTotal > 0) pct = (int) Math.min(100, (current * 100) / finalTotal);
                    else pct = (int) Math.min(100, (idx * 100.0) / count);

                    setProgress(pct, "Caching page " + pageNumber + " · " + idx + "/" + count + " · " + pct + "%");
                });

                done[0] += Math.max(0, item.size);
            } catch (Exception ignored) {}
        }
    }

    private void prefetchNeighbourPages(int current) {
        ArrayList<Integer> keep = new ArrayList<>();
        if (current > 1) keep.add(current - 1);
        keep.add(current);
        if (current < totalPages) keep.add(current + 1);

        cleanupOldPageDirs(keep);

        for (int p : keep) {
            if (p == current) continue;

            new Thread(() -> {
                try {
                    JSONObject data = get("/api/native/files?page=" + p + "&limit=" + PAGE_LIMIT + "&type=" + enc(type));
                    JSONArray arr = data.optJSONArray("items");
                    if (arr == null) arr = data.optJSONArray("files");
                    if (arr == null) return;

                    ArrayList<GalleryItem> list = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        GalleryItem item = new GalleryItem();
                        item.name = o.optString("name", o.optString("filename", "file"));
                        item.folder = o.optString("folder", o.optString("path", ""));
                        item.size = o.optLong("size", 0);
                        item.mime = o.optString("mime", o.optString("mimetype", guessMime(item.name)));
                        item.kind = detectKind(item.name, item.mime, o.optString("kind", o.optString("type", "")));
                        item.key = item.folder + "/" + item.name;
                        item.pageNumber = p;
                        list.add(item);
                    }

                    cachePageFiles(list, p, false);
                    trimCacheIfNeeded();
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private File ensureCached(GalleryItem item, int pageNumber, CacheProgress cb) throws Exception {
        File dir = new File(cacheRoot, "page_" + pageNumber);
        if (!dir.exists()) dir.mkdirs();

        File out = new File(dir, safeFile(item.key));
        if (out.exists() && out.length() > 0) {
            out.setLastModified(System.currentTimeMillis());
            if (cb != null) cb.onProgress(100);
            return out;
        }

        if (!hasEnoughFreeSpaceForCache()) {
            throw new Exception("Need at least 6 GB free storage to cache gallery files. Free: " + fmt(cacheRoot.getUsableSpace()));
        }

        HttpURLConnection c = (HttpURLConnection) new URL(fileUrl(item)).openConnection();
        c.setRequestProperty("X-FC-Token", token);
        c.setConnectTimeout(15000);
        c.setReadTimeout(600000);

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);

        long total = item.size > 0 ? item.size : c.getContentLengthLong();
        long done = 0;

        try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[128 * 1024];
            int n;

            while ((n = in.read(buf)) != -1) {
                if (!hasEnoughFreeSpaceForCache()) {
                    throw new Exception("Cache stopped. Phone free storage is below 6 GB.");
                }

                fos.write(buf, 0, n);
                done += n;

                if (cb != null && total > 0) {
                    int pct = (int) Math.max(1, Math.min(100, (done * 100) / total));
                    cb.onProgress(pct);
                }
            }
        }

        out.setLastModified(System.currentTimeMillis());
        if (cb != null) cb.onProgress(100);
        return out;
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

    
    private boolean hasEnoughFreeSpaceForCache() {
        try {
            if (cacheRoot == null) return false;
            return cacheRoot.getUsableSpace() >= MIN_FREE_SPACE_FOR_CACHE;
        } catch (Exception e) {
            return false;
        }
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
            if (deleted >= CACHE_DELETE_WHEN_FULL) break;
        }

        setProgress(0, "Cache full. Deleted old cache: " + fmt(deleted));
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

    
private Bitmap downloadThumbBitmap(GalleryItem item) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(thumbUrl(item)).openConnection();
    c.setRequestProperty("X-FC-Token", token);
    c.setConnectTimeout(12000);
    c.setReadTimeout(60000);

    int code = c.getResponseCode();
    if (code < 200 || code >= 300) throw new Exception("thumb HTTP " + code);

    try (InputStream in = c.getInputStream()) {
        return BitmapFactory.decodeStream(in);
    }
}

private String thumbUrl(GalleryItem item) throws Exception {
    return baseUrl + "/api/native/thumb?folder=" + enc(item.folder) + "&name=" + enc(item.name) + "&token=" + enc(token);
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
        real.inPreferredConfig = Bitmap.Config.RGB_565;

        return BitmapFactory.decodeFile(file.getAbsolutePath(), real);
    }

    private String safeFile(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
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

    private void setProgress(int pct, String msg) {
        runOnUiThread(() -> {
            if (loadingBar != null) loadingBar.setProgress(Math.max(0, Math.min(100, pct)));
            if (status != null && msg != null) status.setText(msg);
        });
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

    private interface CacheProgress {
        void onProgress(int percent);
    }

    private static class GalleryItem {
        String name;
        String folder;
        String mime;
        String kind;
        String key;
        int pageNumber;
        long size;
    }

@Override
protected void onDestroy() {
    releasePlayer();
    super.onDestroy();
}

}
