package com.familycloud.app;

import android.app.Activity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.os.Build;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.TimeUnit;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.NetworkType;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Constraints;

public class BackupActivity extends Activity {
    private static final int MEDIA_PERMISSION_REQUEST = 9001;
    private SharedPreferences prefs;

    private static final String KEY_ENABLED = "backup_enabled";
    private static final String KEY_WIFI_ONLY = "backup_wifi_only";
    private static final String KEY_PHOTOS = "backup_photos";
    private static final String KEY_VIDEOS = "backup_videos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 42, 28, 42);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 5, 5));

        TextView title = new TextView(this);
        title.setText("Auto Sync Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, 18);

        TextView info = new TextView(this);
        info.setText("Auto Sync controls are saved here. Full background upload worker will be added after APK opens stable.");
        info.setTextColor(Color.rgb(255, 210, 122));
        info.setTextSize(15);
        info.setPadding(0, 0, 0, 18);

        CheckBox enable = makeBox("Enable Auto Sync / Background Backup", KEY_ENABLED, false);
        CheckBox wifi = makeBox("Upload only on Wi-Fi", KEY_WIFI_ONLY, true);
        CheckBox photos = makeBox("Backup Photos", KEY_PHOTOS, true);
        CheckBox videos = makeBox("Backup Videos", KEY_VIDEOS, true);

        Button save = new Button(this);
        save.setText("Save Settings");
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean(KEY_ENABLED, enable.isChecked())
                    .putBoolean(KEY_WIFI_ONLY, wifi.isChecked())
                    .putBoolean(KEY_PHOTOS, photos.isChecked())
                    .putBoolean(KEY_VIDEOS, videos.isChecked())
                    .apply();

            if (enable.isChecked()) {
                if (!hasMediaPermission()) {
                    requestMediaPermission();
                    info.setText("Allow Photos/Videos permission, then tap Save Settings again.");
                    return;
                }

                scheduleAutoSync();
                runSyncNow();
                info.setText("Auto Sync enabled. First sync started.");
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("family_cloud_auto_sync");
                info.setText("Auto Sync disabled.");
            }
        });

        Button syncNow = new Button(this);
        syncNow.setText("Sync Now");
        syncNow.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean(KEY_PHOTOS, photos.isChecked())
                    .putBoolean(KEY_VIDEOS, videos.isChecked())
                    .apply();

            if (!hasMediaPermission()) {
                requestMediaPermission();
                info.setText("Allow Photos/Videos permission, then tap Sync Now again.");
                return;
            }

            runSyncNow();
            info.setText("Manual sync started.");
        });

        Button free = new Button(this);
        free.setText("Free Up Space On This Device");
        free.setOnClickListener(v -> FreeSpaceManager.start(this));

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());

        root.addView(title);
        root.addView(info);
        root.addView(enable);
        root.addView(wifi);
        root.addView(photos);
        root.addView(videos);
        root.addView(save);
        root.addView(syncNow);
        root.addView(free);
        root.addView(back);

        setContentView(root);
    }

    private CheckBox makeBox(String label, String key, boolean def) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(Color.WHITE);
        box.setTextSize(16);
        box.setChecked(prefs.getBoolean(key, def));
        return box;
    }



    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    },
                    MEDIA_PERMISSION_REQUEST
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    MEDIA_PERMISSION_REQUEST
            );
        }
    }

    private void scheduleAutoSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackupWorker.class,
                15,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "family_cloud_auto_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    private void runSyncNow() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class).build();
        WorkManager.getInstance(this).enqueue(request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FreeSpaceManager.FREE_SPACE_DELETE_REQUEST) {
            FreeSpaceManager.onDeleteResult(this, resultCode);
        }
    }

}
