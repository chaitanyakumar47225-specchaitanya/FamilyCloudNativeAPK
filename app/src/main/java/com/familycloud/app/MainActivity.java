package com.familycloud.app;

import android.widget.Spinner;

import android.widget.ArrayAdapter;

import android.view.View;

import android.view.Gravity;

import android.graphics.RectF;

import android.graphics.Paint;

import android.graphics.Canvas;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class MainActivity extends Activity {

    // Compatibility constants for old helper classes like BackupWorker and FreeSpaceManager.
    // Native v1 uses token-based APIs, but these keep old background classes compiling.
    public static final String PREFS = "family_cloud_native";
    public static final String KEY_BASE_URL = "baseUrl";
    public static final String KEY_COOKIE = "cookie";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_EMAIL = "email";
    public static final String ONLINE_URL = "http://100.109.57.8:3000";

    private LinearLayout root;
    private SharedPreferences prefs;
    private String baseUrl;
    private String token;
    private String email;

    private static final String ADMIN_CODE = "8757893577";
    private static final long CACHE_LIMIT = 10L * 1024L * 1024L * 1024L;
    private static final long CACHE_TARGET = 6L * 1024L * 1024L * 1024L;

    private int currentPage = 1;
    private String currentType = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("family_cloud_native", MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "http://100.109.57.8:3000");
        token = prefs.getString("token", "");
        email = prefs.getString("email", "");
        trimCacheAsync();

        if (token.length() > 5) showDashboard();
        else showLogin();
    }

    private void base(String title) {
    ScrollView scroll = new ScrollView(this);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(18, 18, 18, 42);
    root.setBackgroundColor(Color.rgb(5, 5, 5));
    scroll.addView(root);
    setContentView(scroll);

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(8, 8, 8, 18);

    ImageView logo = new ImageView(this);
    logo.setImageResource(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()));
    header.addView(logo, new LinearLayout.LayoutParams(58, 58));

    TextView heading = text("Family Cloud  " + title, 28, Color.WHITE);
    heading.setPadding(14, 0, 0, 0);
    header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    root.addView(header);
}



    private TextView text(String t, int size, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(12, 8, 12, 8);
        return v;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        e.setPadding(18, 14, 18, 14);
        e.setBackgroundColor(Color.rgb(24, 24, 24));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(255, 128, 0));
        return b;
    }

    private void nav() {
    LinearLayout row1 = new LinearLayout(this);
    row1.setOrientation(LinearLayout.HORIZONTAL);

    LinearLayout row2 = new LinearLayout(this);
    row2.setOrientation(LinearLayout.HORIZONTAL);

    Button dash = button("Home");
    Button gal = button("Gallery");
    Button up = button("Upload");
    Button sync = button("Sync");
    Button acc = button("Account");
    Button adm = button("Admin");
    Button out = button("Logout");

    dash.setOnClickListener(v -> showDashboard());
    gal.setOnClickListener(v -> showGallery(1, "all"));
    up.setOnClickListener(v -> showUpload());
    sync.setOnClickListener(v -> startActivity(new Intent(this, BackupActivity.class)));
    acc.setOnClickListener(v -> showAccount());
    adm.setOnClickListener(v -> showAdminLock());
    out.setOnClickListener(v -> logout());

    for (Button b : new Button[]{dash, gal, up, sync}) {
        row1.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    for (Button b : new Button[]{acc, adm, out}) {
        row2.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    root.addView(row1);
    root.addView(row2);
}



    private void showLogin() {
        base("Family Cloud Native");

        EditText server = input("Server URL", baseUrl);
        EditText em = input("Email", email);
        EditText pass = input("Password / admin code", "");

        Button login = button("Login");
        login.setOnClickListener(v -> {
            baseUrl = server.getText().toString().trim();
            email = em.getText().toString().trim().toLowerCase();
            doLogin(email, pass.getText().toString().trim());
        });

        root.addView(text("Native app. No normal WebView pages.", 16, Color.LTGRAY));
        root.addView(server);
        root.addView(em);
        root.addView(pass);
        root.addView(login);
    }

    private void doLogin(String em, String pw) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", em);
                body.put("password", pw);

                JSONObject r = post("/api/native/login", body, false);
                if (!r.optBoolean("ok")) throw new Exception(r.optString("error"));

                token = r.getString("token");
                email = r.getString("email");

                prefs.edit()
                        .putString("baseUrl", baseUrl)
                        .putString("token", token)
                        .putString("email", email)
                        .apply();

                runOnUiThread(this::showDashboard);
            } catch (Exception e) {
                toast("Login failed: " + e.getMessage());
            }
        }).start();
    }

    private void showDashboard() {
    base("Dashboard");
    nav();
    root.addView(text("Loading dashboard...", 16, Color.YELLOW));

    new Thread(() -> {
        try {
            JSONObject d = get("/api/native/dashboard");
            runOnUiThread(() -> {
                base("Dashboard");
                nav();

                int percent = Math.max(0, Math.min(100, d.optInt("percent", 0)));

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.VERTICAL);
                top.setGravity(Gravity.CENTER_HORIZONTAL);
                top.setPadding(12, 12, 12, 12);
                top.setBackgroundColor(Color.rgb(20, 20, 20));

                top.addView(text("Email: " + email, 16, Color.YELLOW));

                CircleProgressView circle = new CircleProgressView(this);
                circle.setPercent(percent);
                top.addView(circle, new LinearLayout.LayoutParams(210, 210));

                top.addView(text("Storage: " + d.optString("usedText") + " / " + d.optString("quotaText"), 20, Color.WHITE));
                top.addView(text("Free: " + d.optString("freeText"), 18, Color.WHITE));
                root.addView(top);

                root.addView(text("Files: " + d.optInt("files"), 18, Color.WHITE));
                root.addView(text("Photos: " + d.optInt("photos"), 18, Color.WHITE));
                root.addView(text("Videos: " + d.optInt("videos"), 18, Color.WHITE));
                root.addView(text("Used: " + percent + "%", 18, Color.WHITE));
                root.addView(text("Temp: " + d.optString("temp", "unknown") + "°C", 18, Color.rgb(255, 152, 0)));
            });
        } catch (Exception e) {
            toast("Dashboard error: " + e.getMessage());
        }
    }).start();
}



    private void showGallery(int page, String type) {
        currentPage = page;
        currentType = type;

        base("Gallery");
        nav();

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);

        Button all = button("All");
        Button photos = button("Photos");
        Button videos = button("Videos");

        all.setOnClickListener(v -> showGallery(1, "all"));
        photos.setOnClickListener(v -> showGallery(1, "photo"));
        videos.setOnClickListener(v -> showGallery(1, "video"));

        filters.addView(all, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        filters.addView(photos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        filters.addView(videos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(filters);

        root.addView(text("Loading files...", 16, Color.YELLOW));

        new Thread(() -> {
            try {
                JSONObject j = get("/api/native/files?page=" + page + "&type=" + URLEncoder.encode(type, "UTF-8"));
                JSONArray items = j.getJSONArray("items");
                int pages = j.optInt("pages", 1);

                runOnUiThread(() -> {
                    base("Gallery");
                    nav();

                    LinearLayout top = new LinearLayout(this);
                    top.setOrientation(LinearLayout.HORIZONTAL);

                    Button prev = button("Previous");
                    Button next = button("Next");
                    Button refresh = button("Refresh");

                    prev.setOnClickListener(v -> showGallery(Math.max(1, currentPage - 1), currentType));
                    next.setOnClickListener(v -> showGallery(Math.min(pages, currentPage + 1), currentType));
                    refresh.setOnClickListener(v -> showGallery(currentPage, currentType));

                    top.addView(prev, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    top.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    top.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    root.addView(top);

                    root.addView(text("Page " + currentPage + " / " + pages + " · " + currentType, 16, Color.YELLOW));

                    for (int i = 0; i < items.length(); i++) {
                        try {
                            addFileRow(items.getJSONObject(i));
                        } catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                toast("Gallery error: " + e.getMessage());
            }
        }).start();
    }

    private void addFileRow(JSONObject item) throws Exception {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 12, 12, 12);
        box.setBackgroundColor(Color.rgb(20, 20, 20));

        String name = item.optString("name");
        String kind = item.optString("kind", "file");
        String size = item.optString("sizeText", "");

        String icon = kind.equals("photo") ? "🖼 " : kind.equals("video") ? "🎥 " : "📄 ";
        box.addView(text(icon + name, 16, Color.WHITE));
        box.addView(text(size, 13, Color.GRAY));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button open = button("Open");
        Button share = button("Share");
        Button del = button("Delete");

        open.setOnClickListener(v -> openNative(item));
        share.setOnClickListener(v -> shareNative(item));
        del.setOnClickListener(v -> confirmDelete(item));

        actions.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        actions.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        box.addView(actions);
        root.addView(box);
    }

    private void openNative(JSONObject item) {
        new Thread(() -> {
            try {
                File file = downloadFile(item, "family-cloud-native-open");
                runOnUiThread(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(uri, guessMime(file.getName()));
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Open with"));
                    } catch (Exception e) {
                        toast("No app can open this file");
                    }
                });
            } catch (Exception e) {
                toast("Open failed: " + e.getMessage());
            }
        }).start();
    }

    private void shareNative(JSONObject item) {
        new Thread(() -> {
            try {
                File file = downloadFile(item, "family-cloud-share");
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(guessMime(file.getName()));
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> startActivity(Intent.createChooser(i, "Share file")));
            } catch (Exception e) {
                toast("Share failed: " + e.getMessage());
            }
        }).start();
    }

    private void confirmDelete(JSONObject item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage(item.optString("name"))
                .setPositiveButton("Delete", (d, w) -> deleteFile(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFile(JSONObject item) {
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("folder", item.optString("folder"));
                b.put("name", item.optString("name"));

                JSONObject r = post("/api/native/delete", b, true);
                if (!r.optBoolean("ok")) throw new Exception(r.optString("error"));

                toast("Deleted");
                runOnUiThread(() -> showGallery(currentPage, currentType));
            } catch (Exception e) {
                toast("Delete failed: " + e.getMessage());
            }
        }).start();
    }

    private void showUpload() {
        base("Upload");
        nav();
        root.addView(text("Upload is placeholder in native v1. Next version will add Android file picker + progress.", 16, Color.YELLOW));
    }

    private void showAccount() {
    base("Account");
    nav();

    root.addView(text("Email: " + email, 18, Color.YELLOW));

    Button zip = button("Create ZIP Backup");
    zip.setOnClickListener(v -> createZipBackup());

    Button changePassword = button("Change Password");
    changePassword.setOnClickListener(v -> showChangePasswordDialog());

    Button deleteData = button("Delete My Whole Data");
    deleteData.setOnClickListener(v -> {
        final EditText confirm = input("Type DELETE", "");
        new AlertDialog.Builder(this)
                .setTitle("Delete all uploaded data?")
                .setMessage("This deletes your whole server data. Type DELETE only if you mean it.")
                .setView(confirm)
                .setPositiveButton("Delete", (d, w) -> {
                    if ("DELETE".equals(confirm.getText().toString().trim())) deleteMyData();
                    else toast("Wrong confirmation");
                })
                .setNegativeButton("Cancel", null)
                .show();
    });

    root.addView(zip);
    root.addView(changePassword);
    root.addView(deleteData);
    root.addView(text("ZIP link validity must be handled by the server: 18 hours from the time link is shown/given.", 14, Color.LTGRAY));
}



    
private void createZipBackup() {
    new Thread(() -> {
        try {
            JSONObject r = post("/api/native/account/zip", new JSONObject(), true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "zip failed"));

            String link = r.optString("url", r.optString("link", r.optString("downloadUrl", "")));
            String expires = r.optString("expiresAt", r.optString("expiresText", "18 hours from now"));

            if (link.length() == 0) throw new Exception("server returned no ZIP link");

            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("ZIP backup ready")
                    .setMessage("Valid until: " + expires + "\n\n" + link)
                    .setPositiveButton("Open", (d, w) -> openLink(link))
                    .setNegativeButton("Close", null)
                    .show());
        } catch (Exception e) {
            toast("ZIP failed: " + e.getMessage());
        }
    }).start();
}

private void showChangePasswordDialog() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);

    EditText oldP = input("Current password", "");
    EditText newP = input("New password", "");
    EditText confirm = input("Confirm new password", "");

    box.addView(oldP);
    box.addView(newP);
    box.addView(confirm);

    new AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(box)
            .setPositiveButton("Change", (d, w) -> changePassword(
                    oldP.getText().toString(),
                    newP.getText().toString(),
                    confirm.getText().toString()
            ))
            .setNegativeButton("Cancel", null)
            .show();
}

private void changePassword(String oldPass, String newPass, String confirm) {
    if (newPass.length() < 6) {
        toast("Password must be at least 6 characters");
        return;
    }

    if (!newPass.equals(confirm)) {
        toast("Passwords do not match");
        return;
    }

    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("oldPassword", oldPass);
            b.put("newPassword", newPass);

            JSONObject r = post("/api/native/account/change-password", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "password failed"));

            toast("Password changed");
        } catch (Exception e) {
            toast("Password failed: " + e.getMessage());
        }
    }).start();
}

private void openLink(String link) {
    try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
    } catch (Exception e) {
        toast("Cannot open link");
    }
}


private void deleteMyData() {
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("confirm", "DELETE");

                JSONObject r = post("/api/native/account/delete-data", b, true);
                if (!r.optBoolean("ok")) throw new Exception(r.optString("error"));

                toast("Data deleted");
                runOnUiThread(this::showDashboard);
            } catch (Exception e) {
                toast("Delete data failed: " + e.getMessage());
            }
        }).start();
    }

    private void showAdminLock() {
        final EditText code = input("Admin code", "");

        new AlertDialog.Builder(this)
                .setTitle("Admin Locked")
                .setView(code)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (ADMIN_CODE.equals(code.getText().toString().trim())) showAdmin();
                    else toast("Wrong admin code");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAdmin() {
    base("Admin");
    nav();
    root.addView(text("Loading admin panel...", 16, Color.YELLOW));

    new Thread(() -> {
        try {
            JSONObject r = get("/api/native/admin/users");
            JSONArray users = r.getJSONArray("users");

            JSONArray drives = new JSONArray();
            String driveError = "";

            try {
                JSONObject d = get("/api/native/admin/drives");
                JSONArray got = d.optJSONArray("drives");
                if (got != null) drives = got;
            } catch (Exception driveEx) {
                driveError = driveEx.getMessage();
            }

            final JSONArray finalDrives = drives;
            final String finalDriveError = driveError;

            runOnUiThread(() -> {
                base("Admin");
                nav();

                root.addView(text("Drives", 22, Color.YELLOW));

                if (finalDrives.length() == 0) {
                    root.addView(text("Drive API missing/empty: " + finalDriveError, 14, Color.rgb(255, 160, 70)));
                }

                for (int i = 0; i < finalDrives.length(); i++) {
                    try {
                        addAdminDrive(finalDrives.getJSONObject(i));
                    } catch (Exception ignored) {}
                }

                root.addView(text("Users", 22, Color.YELLOW));

                for (int i = 0; i < users.length(); i++) {
                    try {
                        addAdminUser(users.getJSONObject(i), finalDrives);
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            toast("Admin error: " + e.getMessage());
        }
    }).start();
}



    private void addAdminUser(JSONObject u, JSONArray drives) {
    try {
        String em = u.optString("email");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 12, 12, 12);
        box.setBackgroundColor(Color.rgb(22, 22, 22));

        box.addView(text(em, 16, Color.WHITE));
        box.addView(text(u.optString("usedText") + " / " + u.optString("quotaText") + " · " + u.optInt("files") + " files", 14, Color.LTGRAY));
        box.addView(text("Current drive: " + u.optString("driveNickname", u.optString("driveId", "default")), 14, Color.rgb(255, 210, 120)));

        EditText q = input("Quota GB", String.valueOf(u.optDouble("quotaGB", 30)));
        Button save = button("Save Quota");

        save.setOnClickListener(v -> saveUserQuota(em, q.getText().toString().trim()));

        box.addView(q);
        box.addView(save);

        if (drives != null && drives.length() > 0) {
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<String> ids = new ArrayList<>();

            for (int i = 0; i < drives.length(); i++) {
                JSONObject d = drives.optJSONObject(i);
                if (d == null) continue;

                String id = d.optString("id", d.optString("driveId", d.optString("path", "")));
                String nickname = d.optString("nickname", d.optString("name", id));

                if (id.length() == 0) continue;

                ids.add(id);
                labels.add(nickname + " (" + id + ")");
            }

            Spinner spin = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
            spin.setAdapter(adapter);

            String current = u.optString("driveId", "");
            int selected = ids.indexOf(current);
            if (selected >= 0) spin.setSelection(selected);

            Button move = button("Move User To Selected Disk");
            move.setOnClickListener(v -> {
                int pos = spin.getSelectedItemPosition();
                if (pos >= 0 && pos < ids.size()) moveUserDisk(em, ids.get(pos));
            });

            box.addView(spin);
            box.addView(move);
        }

        root.addView(box);
    } catch (Exception ignored) {}
}



    
private void addAdminDrive(JSONObject d) {
    String id = d.optString("id", d.optString("driveId", d.optString("path", "")));
    String nickname = d.optString("nickname", d.optString("name", id));

    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(12, 12, 12, 12);
    box.setBackgroundColor(Color.rgb(22, 22, 22));

    box.addView(text("💽 " + nickname, 17, Color.WHITE));
    box.addView(text("ID: " + id, 13, Color.GRAY));
    box.addView(text("Path: " + d.optString("path", d.optString("mount", "unknown")), 13, Color.LTGRAY));
    box.addView(text("Used: " + d.optString("usedText", "?") + " · Free: " + d.optString("freeText", "?") + " · Total: " + d.optString("totalText", "?"), 14, Color.LTGRAY));
    box.addView(text("Temp: " + d.optString("temp", d.optString("temperature", "unknown")) + "°C", 14, Color.rgb(255, 180, 70)));

    EditText nickInput = input("Drive nickname", nickname);
    Button save = button("Save Drive Nickname");
    save.setOnClickListener(v -> saveDriveNickname(id, nickInput.getText().toString().trim()));

    box.addView(nickInput);
    box.addView(save);
    root.addView(box);
}

private void saveDriveNickname(String driveId, String nickname) {
    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("code", ADMIN_CODE);
            b.put("driveId", driveId);
            b.put("nickname", nickname);

            JSONObject r = post("/api/native/admin/drive-nickname", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "nickname failed"));

            toast("Drive nickname saved");
            runOnUiThread(this::showAdmin);
        } catch (Exception e) {
            toast("Drive nickname failed: " + e.getMessage());
        }
    }).start();
}

private void saveUserQuota(String em, String quotaGB) {
    new Thread(() -> {
        try {
            JSONObject b = new JSONObject();
            b.put("email", em);
            b.put("quotaGB", quotaGB);
            b.put("code", ADMIN_CODE);

            JSONObject r = post("/api/native/admin/quota", b, true);
            if (!r.optBoolean("ok")) throw new Exception(r.optString("error"));

            toast("Quota saved");
            runOnUiThread(this::showAdmin);
        } catch (Exception e) {
            toast("Quota failed: " + e.getMessage());
        }
    }).start();
}

private void moveUserDisk(String email, String driveId) {
    new AlertDialog.Builder(this)
            .setTitle("Move user to disk?")
            .setMessage("Move " + email + " to " + driveId + "?")
            .setPositiveButton("Move", (d, w) -> new Thread(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("code", ADMIN_CODE);
                    b.put("email", email);
                    b.put("driveId", driveId);

                    JSONObject r = post("/api/native/admin/user-disk", b, true);
                    if (!r.optBoolean("ok")) throw new Exception(r.optString("error", "move failed"));

                    toast("User moved");
                    runOnUiThread(this::showAdmin);
                } catch (Exception e) {
                    toast("Move failed: " + e.getMessage());
                }
            }).start())
            .setNegativeButton("Cancel", null)
            .show();
}


private void logout() {
        prefs.edit().clear().apply();
        token = "";
        email = "";
        showLogin();
    }

    private JSONObject get(String endpoint) throws Exception {
        URL u = new URL(baseUrl + endpoint);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("X-FC-Token", token);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = read(is);

        if (code < 200 || code >= 300) throw new Exception(body);
        return new JSONObject(body);
    }

    private JSONObject post(String endpoint, JSONObject body, boolean auth) throws Exception {
        URL u = new URL(baseUrl + endpoint);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type", "application/json");
        if (auth) c.setRequestProperty("X-FC-Token", token);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(is);

        return new JSONObject(text);
    }

    private String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private String fileUrl(JSONObject item) throws Exception {
        return baseUrl + "/api/native/file?folder=" +
                URLEncoder.encode(item.optString("folder"), "UTF-8") +
                "&name=" +
                URLEncoder.encode(item.optString("name"), "UTF-8") +
                "&token=" +
                URLEncoder.encode(token, "UTF-8");
    }

    private File downloadFile(JSONObject item, String cacheFolder) throws Exception {
        File dir = new File(getCacheDir(), cacheFolder);
        if (!dir.exists()) dir.mkdirs();

        String name = item.optString("name", "family-cloud-file")
                .replaceAll("[\\\\/:*?\"<>|]", "_");

        File out = new File(dir, name);

        URL u = new URL(fileUrl(item));
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("X-FC-Token", token);
        c.setConnectTimeout(15000);
        c.setReadTimeout(300000);

        long incoming = c.getContentLengthLong();
        trimCacheForIncoming(incoming);

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("server " + code);

        try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }

        out.setLastModified(System.currentTimeMillis());
        trimCacheAsync();
        return out;
    }

    private String guessMime(String name) {
        String n = name.toLowerCase(Locale.ROOT);

        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".heic")) return "image/heic";
        if (n.endsWith(".heif")) return "image/heif";
        if (n.endsWith(".tif") || n.endsWith(".tiff")) return "image/tiff";

        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".avi")) return "video/x-msvideo";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".3gp")) return "video/3gpp";
        if (n.endsWith(".m4v")) return "video/x-m4v";

        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";

        return "*/*";
    }

    private void trimCacheAsync() {
        new Thread(() -> trimCacheForIncoming(0)).start();
    }

    private void trimCacheForIncoming(long incoming) {
        try {
            if (incoming < 0) incoming = 0;

            List<File> files = new ArrayList<>();
            collect(new File(getCacheDir(), "family-cloud-native-open"), files);
            collect(new File(getCacheDir(), "family-cloud-share"), files);
            collect(new File(getCacheDir(), "family-cloud-thumbnails"), files);

            long total = 0;
            for (File f : files) total += f.length();

            if (total + incoming <= CACHE_LIMIT) return;

            Collections.sort(files, Comparator.comparingLong(File::lastModified));

            for (File f : files) {
                if (total + incoming <= CACHE_TARGET) break;
                long size = f.length();
                if (f.delete()) total -= size;
            }
        } catch (Exception ignored) {}
    }

    private void collect(File f, List<File> out) {
        if (f == null || !f.exists()) return;
        if (f.isFile()) {
            out.add(f);
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) collect(k, out);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }


public static class CircleProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int percent = 0;

    public CircleProgressView(Activity context) {
        super(context);
    }

    public void setPercent(int p) {
        percent = Math.max(0, Math.min(100, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h) - 24;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        RectF rect = new RectF(left, top, left + size, top + size);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(12);
        paint.setColor(Color.rgb(55, 55, 55));
        canvas.drawArc(rect, 0, 360, false, paint);

        paint.setColor(Color.rgb(255, 128, 0));
        canvas.drawArc(rect, -90, percent * 3.6f, false, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(42);
        canvas.drawText(percent + "%", w / 2f, h / 2f + 8, paint);

        paint.setColor(Color.LTGRAY);
        paint.setTextSize(15);
        canvas.drawText("used", w / 2f, h / 2f + 34, paint);
    }
}

}
