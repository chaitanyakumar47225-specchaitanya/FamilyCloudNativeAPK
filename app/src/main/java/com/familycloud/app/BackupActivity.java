package com.familycloud.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BackupActivity extends Activity {
    private static final int PERMISSION_REQUEST = 9201;
    private LinearLayout root;
    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        showPage();
    }

    private void showPage() {
        base();

        TextView title = text("Background Backup", 28, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());

        LinearLayout card = card();

        TextView info = text("Choose backup action. Sync Now uploads only phone photos/videos missing from server.", 15, Color.LTGRAY);
        info.setGravity(Gravity.CENTER);

        Button enable = primary("Enable Background Backup");
        enable.setOnClickListener(v -> enableBackground());

        Button syncNow = primary("Sync Now");
        syncNow.setOnClickListener(v -> syncNow());

        Button freeSpace = primary("Free Up Space On Phone");
        freeSpace.setOnClickListener(v -> freeSpace());

        status = text("Ready.", 15, Color.WHITE);
        status.setGravity(Gravity.CENTER);

        Button back = ghost("Back");
        back.setOnClickListener(v -> finish());

        card.addView(info, full());
        card.addView(enable, full());
        card.addView(syncNow, full());
        card.addView(freeSpace, full());
        card.addView(status, full());
        card.addView(back, full());

        root.addView(card, full());
    }

    private void enableBackground() {
        if (!hasPermission()) {
            askPermission();
            status.setText("Allow media permission, then tap Enable again.");
            return;
        }

        prefs.edit().putBoolean("backup_enabled", true).apply();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(BackupWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "sri_ladli_background_backup",
                ExistingPeriodicWorkPolicy.UPDATE,
                req
        );

        status.setText("Background backup enabled.");
        toast("Background backup enabled");
    }

    private void syncNow() {
        if (!hasPermission()) {
            askPermission();
            status.setText("Allow media permission, then tap Sync Now again.");
            return;
        }

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(BackupWorker.class).build();
        WorkManager.getInstance(this).enqueue(req);

        status.setText("Sync started. Missing photos/videos will upload.");
        toast("Sync started");
    }

    private void freeSpace() {
        if (!hasPermission()) {
            askPermission();
            status.setText("Allow media permission, then tap Free Up Space again.");
            return;
        }

        try {
            startActivity(new Intent(this, FreeSpaceManager.class));
        } catch (Exception e) {
            toast("Free space page error: " + e.getMessage());
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            }, PERMISSION_REQUEST);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST);
        }
    }

    private void base() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.rgb(4, 4, 4));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(70));
        root.setBackgroundColor(Color.rgb(4, 4, 4));

        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
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
