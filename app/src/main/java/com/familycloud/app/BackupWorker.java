package com.familycloud.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BackupWorker extends Worker {
    private static final int MAX_FILES_PER_RUN = 25;

    private static final String KEY_ENABLED = "backup_enabled";
    private static final String KEY_PHOTOS = "backup_photos";
    private static final String KEY_VIDEOS = "backup_videos";

    private SharedPreferences prefs;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        prefs = getApplicationContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);

        boolean manual = getInputData().getBoolean("manual", false);
        if (!manual && !prefs.getBoolean(KEY_ENABLED, false)) {
            return Result.success();
        }

        String serverUrl = prefs.getString(MainActivity.KEY_BASE_URL, MainActivity.ONLINE_URL);
        String token = prefs.getString(MainActivity.KEY_TOKEN, prefs.getString("api_token", ""));
        String cookie = prefs.getString(MainActivity.KEY_COOKIE, "");

        if (serverUrl == null || serverUrl.trim().length() == 0) {
            saveError("Missing server URL");
            return Result.success();
        }

        if ((token == null || token.trim().length() == 0) && (cookie == null || cookie.trim().length() == 0)) {
            saveError("Missing login session. Open the app and login once.");
            return Result.success();
        }

        try {
            Set<String> uploadedKeys = new HashSet<>(prefs.getStringSet("uploaded_media_keys", new HashSet<>()));
            Set<String> uploadedUris = new HashSet<>(prefs.getStringSet("uploaded_media_uris", new HashSet<>()));

            ArrayList<MediaItem> pending = collectPendingMedia(uploadedKeys);

            int total = pending.size();
            int done = 0;

            prefs.edit()
                    .putBoolean("sync_running", true)
                    .putInt("sync_total", total)
                    .putInt("sync_done", 0)
                    .putInt("sync_percent", 0)
                    .putString("sync_current_file", total == 0 ? "No new files" : "Starting")
                    .remove("sync_last_error")
                    .apply();

            for (MediaItem item : pending) {
                prefs.edit()
                        .putString("sync_current_file", item.name)
                        .putInt("sync_percent", total > 0 ? (int) ((done * 100f) / total) : 0)
                        .apply();

                boolean ok = uploadOneFile(serverUrl, token, cookie, item);

                if (ok) {
                    done++;
                    uploadedKeys.add(item.key);
                    uploadedUris.add(item.uri.toString());

                    prefs.edit()
                            .putStringSet("uploaded_media_keys", uploadedKeys)
                            .putStringSet("uploaded_media_uris", uploadedUris)
                            .putInt("sync_done", done)
                            .putInt("sync_percent", total > 0 ? (int) ((done * 100f) / total) : 100)
                            .apply();
                }
            }

            prefs.edit()
                    .putBoolean("sync_running", false)
                    .putLong("sync_last_time", System.currentTimeMillis())
                    .putInt("sync_last_count", done)
                    .putString("sync_current_file", "Sync complete")
                    .putInt("sync_percent", 100)
                    .apply();

            return Result.success();

        } catch (Exception e) {
            saveError(e.getMessage());
            return Result.retry();
        }
    }

    private ArrayList<MediaItem> collectPendingMedia(Set<String> uploadedKeys) {
        ArrayList<MediaItem> items = new ArrayList<>();

        if (prefs.getBoolean(KEY_PHOTOS, true)) {
            collectFrom(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, uploadedKeys, items);
        }

        if (prefs.getBoolean(KEY_VIDEOS, true)) {
            collectFrom(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, uploadedKeys, items);
        }

        collectDownloadsMedia(uploadedKeys, items);

        return items;
    }

    private void collectFrom(Uri collection, Set<String> uploadedKeys, ArrayList<MediaItem> items) {
        if (items.size() >= MAX_FILES_PER_RUN) return;

        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
        };

        Cursor cursor = getApplicationContext().getContentResolver().query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        );

        if (cursor == null) return;

        try {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

            while (cursor.moveToNext() && items.size() < MAX_FILES_PER_RUN) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                long size = cursor.getLong(sizeCol);
                String mime = cursor.getString(mimeCol);

                if (name == null || name.trim().length() == 0) name = "media_" + id;
                if (mime == null || mime.trim().length() == 0) mime = guessMime(name);

                String key = collection.toString() + ":" + id + ":" + size;

                if (uploadedKeys.contains(key)) continue;

                MediaItem item = new MediaItem();
                item.uri = Uri.withAppendedPath(collection, String.valueOf(id));
                item.name = name;
                item.mime = mime;
                item.key = key;

                items.add(item);
            }
        } finally {
            cursor.close();
        }
    }


    private void collectDownloadsMedia(Set<String> uploadedKeys, ArrayList<MediaItem> items) {
        if (Build.VERSION.SDK_INT < 29) return;
        if (items.size() >= MAX_FILES_PER_RUN) return;

        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
        };

        Cursor cursor = getApplicationContext().getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        );

        if (cursor == null) return;

        try {
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);

            while (cursor.moveToNext() && items.size() < MAX_FILES_PER_RUN) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                long size = cursor.getLong(sizeCol);
                String mime = cursor.getString(mimeCol);

                if (name == null || name.trim().length() == 0) name = "download_media_" + id;
                if (mime == null || mime.trim().length() == 0) mime = guessMime(name);

                String lowerMime = mime.toLowerCase(Locale.ROOT);
                String lowerName = name.toLowerCase(Locale.ROOT);

                boolean isPhoto = lowerMime.startsWith("image/")
                        || lowerName.endsWith(".jpg")
                        || lowerName.endsWith(".jpeg")
                        || lowerName.endsWith(".png")
                        || lowerName.endsWith(".webp")
                        || lowerName.endsWith(".heic")
                        || lowerName.endsWith(".heif");

                boolean isVideo = lowerMime.startsWith("video/")
                        || lowerName.endsWith(".mp4")
                        || lowerName.endsWith(".mov")
                        || lowerName.endsWith(".mkv")
                        || lowerName.endsWith(".avi")
                        || lowerName.endsWith(".3gp")
                        || lowerName.endsWith(".webm");

                if (!isPhoto && !isVideo) continue;

                String key = MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString() + ":" + id + ":" + size;

                if (uploadedKeys.contains(key)) continue;

                MediaItem item = new MediaItem();
                item.uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                item.name = name;
                item.mime = mime;
                item.key = key;

                items.add(item);
            }
        } finally {
            cursor.close();
        }
    }

    private boolean uploadOneFile(String serverUrl, String token, String cookie, MediaItem item) throws Exception {
        String cleanServer = serverUrl.trim();
        while (cleanServer.endsWith("/")) cleanServer = cleanServer.substring(0, cleanServer.length() - 1);

        URL url = new URL(cleanServer + "/upload");
        String boundary = "FamilyCloudBoundary" + System.currentTimeMillis();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(180000);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        if (token != null && token.trim().length() > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("X-FC-Token", token);
        }

        if (cookie != null && cookie.trim().length() > 0) {
            conn.setRequestProperty("Cookie", cookie);
        }

        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());

        writeField(out, boundary, "deviceKey", item.key);
        writeField(out, boundary, "localUri", item.uri.toString());

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"files\"; filename=\"" + safeFileName(item.name) + "\"\r\n");
        out.writeBytes("Content-Type: " + item.mime + "\r\n\r\n");

        InputStream input = new BufferedInputStream(getApplicationContext().getContentResolver().openInputStream(item.uri));

        if (input == null) {
            out.close();
            conn.disconnect();
            return false;
        }

        byte[] buffer = new byte[1024 * 256];
        int read;

        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        input.close();

        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        conn.disconnect();

        return code >= 200 && code < 400;
    }

    private void writeField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value == null ? "" : value);
        out.writeBytes("\r\n");
    }

    private String safeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String guessMime(String name) {
        String ext = "";
        int dot = name.lastIndexOf(".");
        if (dot >= 0 && dot < name.length() - 1) {
            ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "application/octet-stream" : mime;
    }

    private void saveError(String msg) {
        prefs.edit()
                .putBoolean("sync_running", false)
                .putString("sync_last_error", msg == null ? "Unknown error" : msg)
                .putString("sync_current_file", "Error")
                .apply();
    }

    private static class MediaItem {
        Uri uri;
        String name;
        String mime;
        String key;
    }
}
