package com.familycloud.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BackupActivity extends Activity {
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
            info.setText("Settings saved.");
        });

        Button free = new Button(this);
        free.setText("Free Up Space On This Device");
        free.setOnClickListener(v -> info.setText("Free up space will work after successful Auto Sync upload records exist. First we are making APK open stable."));

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
}
