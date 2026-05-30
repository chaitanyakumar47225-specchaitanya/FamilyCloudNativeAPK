package com.familycloud.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    public static final String PREFS = "family_cloud_prefs";
    public static final String KEY_COOKIE = "cookie";
    public static final String KEY_LOGIN_OK = "login_ok";
    public static final String KEY_ONLINE_COOKIE = "cookie_online";
    public static final String KEY_OFFLINE_COOKIE = "cookie_offline";
    public static final String KEY_LOGIN_EMAIL = "login_email";
    public static final String ONLINE_URL = "";
    public static final String LOCAL_URL = "";

    // Compatibility constants for old BackupWorker / FreeSpaceManager code.
    public static final String PREF_SERVER_URL = "base_url";
    public static final String SERVER_URL = "base_url";
    public static final String PREF_BASE_URL = "base_url";
    public static final String KEY_SERVER_URL = "base_url";
    public static final String KEY_BASE_URL_PUBLIC = "base_url";
    public static final String PREF_EMAIL = "email";
    public static final String KEY_EMAIL = "email";
    public static final String PREF_USER_EMAIL = "email";
    public static final String PREF_LOCAL_URL = "local_url";
    public static final String PREF_ONLINE_URL = "online_url";
    public static final String PREF_MODE = "mode";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_ONLINE = "online";

    public static final String KEY_BASE_URL = "base_url";
    private static final int PICK_FILES = 4001;

    private static final String ADMIN_CODE = "8757893577";

    private final int BG = Color.rgb(8, 10, 18);
    private final int CARD = Color.rgb(23, 27, 40);
    private final int CARD2 = Color.rgb(31, 35, 52);
    private final int TEXT = Color.rgb(245, 247, 255);
    private final int MUTED = Color.rgb(168, 174, 190);
    private final int YELLOW = Color.rgb(255, 204, 51);
    private final int RED = Color.rgb(255, 72, 72);

    private SharedPreferences prefs;
    private String baseUrl;

    private FrameLayout content;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(4);

    private TextView uploadStatus;
    private ProgressBar uploadProgress;
    private TextView uploadFailures;

    private final ArrayList<FileItem> galleryFiles = new ArrayList<>();
    private GalleryAdapter galleryAdapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrl = prefs.getString(KEY_BASE_URL, "");

        requestBasicPermissions();
        buildShell();

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            showSetupDialog();
        } else {
            showHome();
        }
    }

    private void requestBasicPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.POST_NOTIFICATIONS"
            }, 11);
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 10);
            }
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(10));

        TextView name = new TextView(this);
        name.setText("Family Cloud");
        name.setTextColor(TEXT);
        name.setTextSize(25);
        name.setTypeface(Typeface.DEFAULT_BOLD);

        TextView sub = new TextView(this);
        sub.setText("Native APK · Backup · Gallery · Storage");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);

        top.addView(name);
        top.addView(sub);

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(5), dp(8), dp(5), dp(8));
        nav.setBackgroundColor(Color.rgb(12, 14, 24));

        addNav(nav, "Home", v -> showHome());
        addNav(nav, "Gallery", v -> showGallery());
        addNav(nav, "Backup", v -> showBackup());
        addNav(nav, "Storage", v -> showStorage());
        addNav(nav, "Admin", v -> showAdmin());
        addNav(nav, "Settings", v -> showSettings());

        root.addView(top);
        root.addView(content);
        root.addView(nav);

        setContentView(root);
    }

    private void addNav(LinearLayout nav, String text, View.OnClickListener l) {
        Button b = button(text, CARD2, TEXT);
        b.setTextSize(10);
        b.setPadding(0, dp(7), 0, dp(7));
        b.setOnClickListener(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(b, lp);
    }


    private void showLoginGate() {
        String savedCookie = getActiveCookie();
        boolean loginOk = prefs.getBoolean(KEY_LOGIN_OK, false);

        if (loginOk && savedCookie != null && savedCookie.trim().length() > 0) {
            topBar.setVisibility(View.VISIBLE);
            bottomNav.setVisibility(View.VISIBLE);
            showHome();
            return;
        }

        showLoginScreen();
    }

    private void showLoginScreen() {
        topBar.setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);

        LinearLayout box = baseNoTitle();
        box.setPadding(dp(22), dp(34), dp(22), dp(22));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView icon = new TextView(this);
        icon.setText("🔐");
        icon.setTextSize(54);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);

        TextView title = new TextView(this);
        title.setText("Login Required");
        title.setTextColor(TEXT);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView info = small("Mode: " + currentMode + "\nServer: " + baseUrl + "\nLogin once. After success, this APK keeps your session saved.");
        info.setGravity(Gravity.CENTER);
        box.addView(card("Connection", info));

        final EditText email = new EditText(this);
        email.setSingleLine(true);
        email.setHint("Email / username");
        email.setText(prefs.getString(KEY_LOGIN_EMAIL, ""));
        email.setTextColor(TEXT);
        email.setHintTextColor(MUTED);
        email.setPadding(dp(14), 0, dp(14), 0);
        email.setBackground(round(CARD2, dp(18), YELLOW, 1));

        final EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Password");
        password.setTextColor(TEXT);
        password.setHintTextColor(MUTED);
        password.setPadding(dp(14), 0, dp(14), 0);
        password.setInputType(0x00000081);
        password.setBackground(round(CARD2, dp(18), YELLOW, 1));

        box.addView(small("Email / Username"));
        box.addView(email, new LinearLayout.LayoutParams(-1, dp(54)));
        box.addView(small("Password"));
        box.addView(password, new LinearLayout.LayoutParams(-1, dp(54)));

        Button login = button("Login and Continue", YELLOW, Color.BLACK);
        login.setTextSize(15);
        login.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String e = email.getText().toString().trim();
                String pass = password.getText().toString();

                if (e.length() == 0) {
                    toast("Enter email / username");
                    return;
                }

                doLogin(e, pass);
            }
        });
        box.addView(login, bigLp());

        Button back = button("Go Back", CARD2, TEXT);
        back.setTextSize(15);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSavedUrlScreen(currentMode);
            }
        });
        box.addView(back, bigLp());

        setScreen(scroll(box));
    }

    private void doLogin(final String email, final String password) {
        toast("Logging in...");

        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    try {
                        postLoginSync("/login", email, password);
                    } catch (Exception first) {
                        postLoginSync("/api/login", email, password);
                    }

                    prefs.edit()
                            .putString(KEY_LOGIN_EMAIL, email)
                            .putBoolean(KEY_LOGIN_OK, true)
                            .apply();

                    ui.post(new Runnable() {
                        @Override public void run() {
                            toast("Login saved");
                            topBar.setVisibility(View.VISIBLE);
                            bottomNav.setVisibility(View.VISIBLE);
                            showHome();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            toast("Login failed: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void postLoginSync(String endpoint, String email, String password) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(abs(endpoint)).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setDoInput(true);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        applySavedCookie(c);

        String body = "email=" + URLEncoder.encode(email, "UTF-8")
                + "&username=" + URLEncoder.encode(email, "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");

        DataOutputStream out = new DataOutputStream(c.getOutputStream());
        out.writeBytes(body);
        out.flush();
        out.close();

        int code = c.getResponseCode();
        saveCookieFromResponse(c);

        InputStream response = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = read(response);

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + text);
        }

        String cookie = getActiveCookie();
        if (cookie == null || cookie.trim().length() == 0) {
            // Some backends return success without Set-Cookie. Keep login flag anyway.
            prefs.edit().putBoolean(KEY_LOGIN_OK, true).apply();
        }
    }

    private String getActiveCookieKey() {
        if (MODE_ONLINE.equals(currentMode)) return KEY_ONLINE_COOKIE;
        return KEY_OFFLINE_COOKIE;
    }

    private String getActiveCookie() {
        String modeCookie = prefs.getString(getActiveCookieKey(), "");
        if (modeCookie != null && modeCookie.trim().length() > 0) return modeCookie;
        return prefs.getString(KEY_COOKIE, "");
    }

    private void applySavedCookie(HttpURLConnection c) {
        String cookie = getActiveCookie();
        if (cookie != null && cookie.trim().length() > 0) {
            c.setRequestProperty("Cookie", cookie);
        }
    }

    private void saveCookieFromResponse(HttpURLConnection c) {
        String setCookie = c.getHeaderField("Set-Cookie");
        if (setCookie == null || setCookie.trim().length() == 0) return;

        String cookie = setCookie.split(";", 2)[0].trim();
        if (cookie.length() == 0) return;

        prefs.edit()
                .putString(KEY_COOKIE, cookie)
                .putString(getActiveCookieKey(), cookie)
                .putBoolean(KEY_LOGIN_OK, true)
                .apply();
    }

    private void clearSavedLogin() {
        prefs.edit()
                .remove(KEY_COOKIE)
                .remove(KEY_ONLINE_COOKIE)
                .remove(KEY_OFFLINE_COOKIE)
                .remove(KEY_LOGIN_OK)
                .apply();
    }


    private void setScreen(View v) {
        content.removeAllViews();
        content.addView(v);
    }

    private LinearLayout base(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(20));

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(TEXT);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(12));
        box.addView(h);

        return box;
    }

    private ScrollView scroll(LinearLayout box) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(BG);
        s.addView(box);
        return s;
    }

    private void showSetupDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), 0, dp(10), 0);

        TextView t = new TextView(this);
        t.setText("Enter Family Cloud server URL.\nExample: http://192.168.1.15:3000\nDo not add /gallery.");
        t.setTextColor(Color.DKGRAY);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("http://192.168.1.15:3000");
        input.setText(baseUrl == null ? "" : baseUrl);

        box.addView(t);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Connect Server")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("Save", (d, which) -> {
                    String u = input.getText().toString().trim();
                    if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
                    if (!u.startsWith("http")) {
                        toast("Use full URL like http://192.168.1.15:3000");
                        showSetupDialog();
                        return;
                    }
                    baseUrl = u;
                    prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
                    showHome();
                })
                .show();
    }

    private void showHome() {
        LinearLayout box = base("Dashboard");

        TextView server = small("Checking server...");
        TextView dashboard = small("Loading dashboard...");

        box.addView(card("Server Status", server));
        box.addView(card("Storage / Backup Summary", dashboard));
        box.addView(card("Native Mode", small("No WebView. Same Family Cloud purpose, rebuilt as native Android UI.")));

        Button refresh = button("Refresh", YELLOW, Color.BLACK);
        refresh.setOnClickListener(v -> showHome());
        box.addView(refresh, lp());

        setScreen(scroll(box));

        getText("/api/health", new TextCallback() {
            public void ok(String text) { server.setText(pretty(text)); }
            public void fail(String err) { server.setText("Server not reachable:\n" + err); }
        });

        getText("/api/dashboard", new TextCallback() {
            public void ok(String text) { dashboard.setText(pretty(text)); }
            public void fail(String err) { dashboard.setText("Dashboard API failed:\n" + err); }
        });
    }

    private void showGallery() {
        LinearLayout main = base("Gallery");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button refresh = button("Refresh", YELLOW, Color.BLACK);
        refresh.setOnClickListener(v -> loadGallery());

        Button upload = button("Upload", RED, Color.WHITE);
        upload.setOnClickListener(v -> openFilePicker());

        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView gap = new TextView(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        row.addView(upload, new LinearLayout.LayoutParams(0, dp(48), 1));

        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp(4));
        grid.setVerticalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);

        galleryAdapter = new GalleryAdapter(this, galleryFiles);
        grid.setAdapter(galleryAdapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < galleryFiles.size()) showPreview(galleryFiles.get(pos));
        });

        main.addView(row);
        main.addView(small("Native grid preview. It reads /api/files."));
        main.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        setScreen(main);
        loadGallery();
    }

    private void loadGallery() {
        if (galleryAdapter != null) {
            galleryFiles.clear();
            galleryAdapter.notifyDataSetChanged();
        }

        getText("/api/files", new TextCallback() {
            public void ok(String text) {
                galleryFiles.clear();
                galleryFiles.addAll(parseFiles(text));
                if (galleryAdapter != null) galleryAdapter.notifyDataSetChanged();
                if (galleryFiles.isEmpty()) toast("No files returned by /api/files");
            }
            public void fail(String err) {
                toast("Gallery API failed: " + err);
            }
        });
    }

    private void showPreview(FileItem item) {
        LinearLayout box = base(item.name);

        Button back = button("Back", CARD2, TEXT);
        back.setOnClickListener(v -> showGallery());
        box.addView(back, lp());

        if (item.isImage()) {
            ImageView img = new ImageView(this);
            img.setBackgroundColor(Color.BLACK);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(img, new LinearLayout.LayoutParams(-1, dp(430)));
            loadBitmap(item.url, img);
        } else {
            TextView icon = new TextView(this);
            icon.setText(item.isVideo() ? "🎬 Video" : "📄 File");
            icon.setTextColor(TEXT);
            icon.setTextSize(30);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(round(CARD, dp(22), 0, 0));
            box.addView(icon, new LinearLayout.LayoutParams(-1, dp(220)));
        }

        box.addView(card("Details", small("Name: " + item.name + "\nType: " + item.mime + "\nSize: " + item.sizeText())));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button open = button("Open", YELLOW, Color.BLACK);
        open.setOnClickListener(v -> openExternal(item));

        Button download = button("Download", RED, Color.WHITE);
        download.setOnClickListener(v -> download(item));

        actions.addView(open, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView sp = new TextView(this);
        actions.addView(sp, new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(download, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);

        setScreen(scroll(box));
    }

    private void showBackup() {
        LinearLayout box = base("Backup");

        box.addView(card("Manual Backup", small("Choose files from phone. App uploads to /upload and shows progress.")));

        Button pick = button("Choose Files", YELLOW, Color.BLACK);
        pick.setOnClickListener(v -> openFilePicker());
        box.addView(pick, lp());

        uploadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        uploadProgress.setMax(100);
        box.addView(uploadProgress, lp());

        uploadStatus = small("Waiting for files...");
        uploadFailures = small("");

        box.addView(card("Progress", uploadStatus));
        box.addView(card("Failures", uploadFailures));

        setScreen(scroll(box));
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, "Select files"), PICK_FILES);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == PICK_FILES && res == RESULT_OK && data != null) {
            ArrayList<Uri> list = new ArrayList<>();
            ClipData clip = data.getClipData();

            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) list.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                list.add(data.getData());
            }

            if (!list.isEmpty()) {
                showBackup();
                uploadFiles(list);
            }
        }
    }

    private void uploadFiles(ArrayList<Uri> uris) {
        uploadProgress.setMax(uris.size());
        uploadProgress.setProgress(0);
        uploadStatus.setText("Starting upload 0 / " + uris.size());
        uploadFailures.setText("");

        io.execute(() -> {
            int done = 0;
            ArrayList<String> failed = new ArrayList<>();
            long start = System.currentTimeMillis();

            for (Uri uri : uris) {
                String name = getDisplayName(uri);
                int next = done + 1;
                ui.post(() -> uploadStatus.setText("Uploading " + next + " / " + uris.size() + "\n" + name));

                try {
                    postMultipartSync("/upload", uri, name);
                } catch (Exception e) {
                    failed.add(name + " → " + e.getMessage());
                }

                done++;
                int finalDone = done;
                long elapsed = Math.max(1, System.currentTimeMillis() - start);
                long per = elapsed / finalDone;
                long rem = per * (uris.size() - finalDone);

                ui.post(() -> {
                    uploadProgress.setProgress(finalDone);
                    uploadStatus.setText("Uploaded " + finalDone + " / " + uris.size() + "\nETA: " + formatMillis(rem));
                    uploadFailures.setText(joinLines(failed));
                });
            }

            ui.post(() -> {
                uploadStatus.setText("Upload complete: " + uris.size() + " files processed.");
                if (failed.isEmpty()) uploadFailures.setText("No failures.");
            });
        });
    }

    private void showStorage() {
        LinearLayout box = base("Storage");
        TextView data = small("Loading...");
        box.addView(card("Drive / Quota / Temperature", data));

        Button refresh = button("Refresh", YELLOW, Color.BLACK);
        refresh.setOnClickListener(v -> showStorage());
        box.addView(refresh, lp());

        setScreen(scroll(box));

        getText("/api/dashboard", new TextCallback() {
            public void ok(String text) { data.setText(pretty(text)); }
            public void fail(String err) { data.setText("Storage API failed:\n" + err); }
        });
    }

    
    private void showAdmin() {
        if (!prefs.getBoolean("admin_unlocked", false)) {
            showAdminLock();
            return;
        }

        LinearLayout box = baseNoTitle();
        box.setPadding(dp(12), dp(12), dp(12), dp(30) + getSafeBottomPadding());

        TextView heading = new TextView(this);
        heading.setText("Admin Panel");
        heading.setTextColor(YELLOW);
        heading.setTextSize(26);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, 0, 0, dp(10));
        box.addView(heading);

        final LinearLayout metricsGrid = new LinearLayout(this);
        metricsGrid.setOrientation(LinearLayout.VERTICAL);
        box.addView(metricsGrid);

        LinearLayout addUser = new LinearLayout(this);
        addUser.setOrientation(LinearLayout.VERTICAL);
        addUser.setPadding(dp(14), dp(14), dp(14), dp(14));
        addUser.setBackground(round(Color.rgb(33, 12, 10), dp(16), Color.rgb(150, 90, 0), 1));

        TextView addTitle = new TextView(this);
        addTitle.setText("Add New User");
        addTitle.setTextColor(YELLOW);
        addTitle.setTextSize(23);
        addTitle.setTypeface(Typeface.DEFAULT_BOLD);

        TextView addSub = small("Creates user, sets temporary password, sets allowed storage, and chooses which drive stores the user files.");

        final EditText newEmail = adminInput("user@example.com");
        final EditText newPass = adminInput("Temporary password");
        final EditText newStorage = adminInput("Allowed storage, example: 10GB");
        final EditText newDrive = adminInput("Drive name, example: hdd 512 gb");
        final EditText newRole = adminInput("Role, example: User");

        Button create = button("Create User", YELLOW, Color.BLACK);
        create.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/add-user",
                        "email=" + enc(newEmail.getText().toString()) +
                        "&password=" + enc(newPass.getText().toString()) +
                        "&storage=" + enc(newStorage.getText().toString()) +
                        "&drive=" + enc(newDrive.getText().toString()) +
                        "&role=" + enc(newRole.getText().toString()),
                        new TextCallback() {
                            @Override public void ok(String text) {
                                toast("User created");
                                showAdmin();
                            }

                            @Override public void fail(String err) {
                                toast("Add user failed: " + err);
                            }
                        });
            }
        });

        addUser.addView(addTitle);
        addUser.addView(addSub);
        addUser.addView(newEmail, inputLp());
        addUser.addView(newPass, inputLp());
        addUser.addView(newStorage, inputLp());
        addUser.addView(newDrive, inputLp());
        addUser.addView(newRole, inputLp());
        addUser.addView(create, lp());

        box.addView(addUser, lp());

        TextView usersTitle = new TextView(this);
        usersTitle.setText("Users");
        usersTitle.setTextColor(YELLOW);
        usersTitle.setTextSize(23);
        usersTitle.setTypeface(Typeface.DEFAULT_BOLD);
        usersTitle.setPadding(0, dp(12), 0, dp(6));
        box.addView(usersTitle);

        final LinearLayout usersBox = new LinearLayout(this);
        usersBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(usersBox);

        TextView drivesTitle = new TextView(this);
        drivesTitle.setText("Physical Drives");
        drivesTitle.setTextColor(YELLOW);
        drivesTitle.setTextSize(23);
        drivesTitle.setTypeface(Typeface.DEFAULT_BOLD);
        drivesTitle.setPadding(0, dp(12), 0, dp(6));
        box.addView(drivesTitle);

        final LinearLayout drivesBox = new LinearLayout(this);
        drivesBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(drivesBox);

        Button refresh = button("Refresh Admin Data", YELLOW, Color.BLACK);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                loadAdminData(metricsGrid, usersBox, drivesBox);
            }
        });
        box.addView(refresh, lp());

        Button lock = button("Lock Admin Panel", RED, Color.WHITE);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("admin_unlocked", false).apply();
                showAdminLock();
            }
        });
        box.addView(lock, lp());

        setScreen(scroll(box));
        loadAdminData(metricsGrid, usersBox, drivesBox);
    }



    private void showAdminLock() {
        topBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);

        LinearLayout box = baseNoTitle();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(22), dp(40), dp(22), dp(40) + getSafeBottomPadding());

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(56);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);

        TextView title = new TextView(this);
        title.setText("Admin Locked");
        title.setTextColor(YELLOW);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = small("Enter admin code to unlock protected server controls.");
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, lp());

        final EditText code = adminInput("Admin code");
        code.setInputType(0x00000012);
        box.addView(code, inputLp());

        Button unlock = button("Unlock Admin", YELLOW, Color.BLACK);
        unlock.setTextSize(15);
        unlock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ADMIN_CODE.equals(code.getText().toString().trim())) {
                    prefs.edit().putBoolean("admin_unlocked", true).apply();
                    toast("Admin unlocked");
                    showAdmin();
                } else {
                    toast("Wrong admin code");
                }
            }
        });
        box.addView(unlock, bigLp());

        Button back = button("Back", CARD2, TEXT);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHome();
            }
        });
        box.addView(back, bigLp());

        setScreen(scroll(box));
    }

    private void loadAdminData(final LinearLayout metricsGrid, final LinearLayout usersBox, final LinearLayout drivesBox) {
        metricsGrid.removeAllViews();
        usersBox.removeAllViews();
        drivesBox.removeAllViews();

        metricsGrid.addView(card("Loading", small("Fetching admin data...")));
        usersBox.addView(card("Loading", small("Fetching users...")));
        drivesBox.addView(card("Loading", small("Fetching drives...")));

        getText("/api/admin/dashboard", new TextCallback() {
            @Override public void ok(String text) {
                metricsGrid.removeAllViews();
                renderAdminMetrics(metricsGrid, text);
            }

            @Override public void fail(String err) {
                getText("/api/dashboard", new TextCallback() {
                    @Override public void ok(String text) {
                        metricsGrid.removeAllViews();
                        renderAdminMetrics(metricsGrid, text);
                    }

                    @Override public void fail(String e2) {
                        metricsGrid.removeAllViews();
                        metricsGrid.addView(card("Admin Dashboard Failed", small(err + "\n" + e2)));
                    }
                });
            }
        });

        getText("/api/admin/users", new TextCallback() {
            @Override public void ok(String text) {
                usersBox.removeAllViews();
                renderAdminUsers(usersBox, text);
            }

            @Override public void fail(String err) {
                getText("/api/users", new TextCallback() {
                    @Override public void ok(String text) {
                        usersBox.removeAllViews();
                        renderAdminUsers(usersBox, text);
                    }

                    @Override public void fail(String e2) {
                        usersBox.removeAllViews();
                        usersBox.addView(card("Users Failed", small(err + "\n" + e2)));
                    }
                });
            }
        });

        getText("/api/admin/drives", new TextCallback() {
            @Override public void ok(String text) {
                drivesBox.removeAllViews();
                renderAdminDrives(drivesBox, text);
            }

            @Override public void fail(String err) {
                getText("/api/drives", new TextCallback() {
                    @Override public void ok(String text) {
                        drivesBox.removeAllViews();
                        renderAdminDrives(drivesBox, text);
                    }

                    @Override public void fail(String e2) {
                        drivesBox.removeAllViews();
                        drivesBox.addView(card("Physical Drives Failed", small(err + "\n" + e2)));
                    }
                });
            }
        });
    }

    private void renderAdminMetrics(LinearLayout parent, String text) {
        try {
            JSONObject obj = new JSONObject(text);

            String totalUsers = findJsonText(obj, new String[]{"totalUsers", "usersCount", "userCount"});
            String realUsed = findJsonText(obj, new String[]{"realUserUsed", "realUsed", "actualUsed", "usedByUsers"});
            String totalGiven = findJsonText(obj, new String[]{"totalStorageGiven", "storageGiven", "quotaGiven"});
            String realFiles = findJsonText(obj, new String[]{"realFiles", "totalFiles", "files"});
            String driveCount = findJsonText(obj, new String[]{"driveCount", "drivesCount", "totalDrives"});
            String givenNotUsed = findJsonText(obj, new String[]{"storageGivenNotUsed", "givenNotUsed", "unusedGiven"});

            LinearLayout r1 = new LinearLayout(this);
            r1.setOrientation(LinearLayout.HORIZONTAL);
            r1.addView(adminMetric("Total Users", emptyDash(totalUsers)), new LinearLayout.LayoutParams(0, dp(84), 1));
            r1.addView(adminMetric("Real User Used", emptyDash(realUsed)), new LinearLayout.LayoutParams(0, dp(84), 1));

            LinearLayout r2 = new LinearLayout(this);
            r2.setOrientation(LinearLayout.HORIZONTAL);
            r2.addView(adminMetric("Total Storage Given", emptyDash(totalGiven)), new LinearLayout.LayoutParams(0, dp(84), 1));
            r2.addView(adminMetric("Real Files", emptyDash(realFiles)), new LinearLayout.LayoutParams(0, dp(84), 1));

            LinearLayout r3 = new LinearLayout(this);
            r3.setOrientation(LinearLayout.HORIZONTAL);
            r3.addView(adminMetric("Drive Count", emptyDash(driveCount)), new LinearLayout.LayoutParams(0, dp(84), 1));
            r3.addView(adminMetric("Storage Given Not Used", emptyDash(givenNotUsed)), new LinearLayout.LayoutParams(0, dp(84), 1));

            parent.addView(r1, lp());
            parent.addView(r2, lp());
            parent.addView(r3, lp());
        } catch (Exception e) {
            parent.addView(card("Raw Dashboard", small(pretty(text))));
        }
    }

    private View adminMetric(String label, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        c.setBackground(round(Color.rgb(34, 12, 10), dp(16), Color.rgb(150, 90, 0), 1));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(YELLOW);
        l.setTextSize(11);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(YELLOW);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);

        c.addView(l);
        c.addView(v);
        return c;
    }

    private void renderAdminUsers(LinearLayout parent, String text) {
        try {
            JSONArray arr = extractArray(text, new String[]{"users", "data", "items"});
            if (arr == null) {
                parent.addView(card("Users", small(pretty(text))));
                return;
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject u = arr.getJSONObject(i);

                String email = first(u, "email", "username", "id", "name");
                String role = first(u, "role", "type");
                String status = first(u, "status", "active", "blocked");
                String drive = first(u, "drive", "driveName", "storageDrive");
                String actual = first(u, "actual", "actualPath", "path");
                String quota = first(u, "storage", "quota", "allowedStorage");
                String used = first(u, "used", "usedStorage", "realUsed");
                String files = first(u, "files", "fileCount", "totalFiles");
                String photos = first(u, "photos", "photoCount");
                String videos = first(u, "videos", "videoCount");

                parent.addView(userAdminCard(email, role, status, drive, actual, quota, used, files, photos, videos));
            }
        } catch (Exception e) {
            parent.addView(card("Users", small(pretty(text))));
        }
    }

    private View userAdminCard(final String email, String role, String status, String drive, String actual, String quota, String used, String files, String photos, String videos) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackground(round(Color.rgb(20, 8, 7), dp(16), Color.rgb(70, 35, 20), 1));

        TextView top = new TextView(this);
        top.setText(emptyDash(email));
        top.setTextColor(TEXT);
        top.setTextSize(15);
        top.setTypeface(Typeface.DEFAULT_BOLD);

        TextView details = small(
                "Role: " + emptyDash(role) +
                "\nStatus: " + emptyDash(status) +
                "\nStorage Drive: " + emptyDash(drive) +
                "\nActual: " + emptyDash(actual) +
                "\nStorage: " + emptyDash(used) + " / " + emptyDash(quota) +
                "\nFiles: " + emptyDash(files) + "    Photos: " + emptyDash(photos) + "    Videos: " + emptyDash(videos)
        );

        final EditText moveDrive = adminInput("Drive name");
        Button move = button("Move User To Drive", YELLOW, Color.BLACK);
        move.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/move-user-drive",
                        "email=" + enc(email) + "&drive=" + enc(moveDrive.getText().toString()),
                        adminToastReload("User moved"));
            }
        });

        final EditText storage = adminInput("Storage, example: 10GB");
        Button changeStorage = button("Change Storage", YELLOW, Color.BLACK);
        changeStorage.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/change-storage",
                        "email=" + enc(email) + "&storage=" + enc(storage.getText().toString()),
                        adminToastReload("Storage changed"));
            }
        });

        final EditText pass = adminInput("New password");
        Button reset = button("Reset Password", YELLOW, Color.BLACK);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/reset-password",
                        "email=" + enc(email) + "&password=" + enc(pass.getText().toString()),
                        adminToastReload("Password reset"));
            }
        });

        Button block = button("Block", RED, Color.WHITE);
        block.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/block-user", "email=" + enc(email), adminToastReload("User blocked"));
            }
        });

        Button unblock = button("Unblock", CARD2, TEXT);
        unblock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/unblock-user", "email=" + enc(email), adminToastReload("User unblocked"));
            }
        });

        final EditText del = adminInput("Type DELETE");
        Button delete = button("Delete User + Files", RED, Color.WHITE);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!"DELETE".equals(del.getText().toString().trim())) {
                    toast("Type DELETE first");
                    return;
                }
                adminPost("/api/admin/delete-user",
                        "email=" + enc(email) + "&confirm=DELETE",
                        adminToastReload("User deleted"));
            }
        });

        c.addView(top);
        c.addView(details);
        c.addView(moveDrive, inputLp());
        c.addView(move, lp());
        c.addView(storage, inputLp());
        c.addView(changeStorage, lp());
        c.addView(pass, inputLp());
        c.addView(reset, lp());
        c.addView(block, lp());
        c.addView(unblock, lp());
        c.addView(del, inputLp());
        c.addView(delete, lp());

        LinearLayout.LayoutParams lp = lp();
        c.setLayoutParams(lp);
        return c;
    }

    private void renderAdminDrives(LinearLayout parent, String text) {
        try {
            JSONArray arr = extractArray(text, new String[]{"drives", "data", "items"});
            if (arr == null) {
                parent.addView(card("Physical Drives", small(pretty(text))));
                return;
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject d = arr.getJSONObject(i);

                String name = first(d, "name", "driveName", "label");
                String system = first(d, "system", "device", "path");
                String userRoot = first(d, "userRoot", "usersRoot", "mount", "mountPoint");
                String size = first(d, "size", "total", "totalSize");
                String model = first(d, "model");
                String temp = first(d, "temperature", "temp");
                String status = first(d, "status", "health");
                String used = first(d, "used", "driveUsed");
                String free = first(d, "free", "driveFree");
                String given = first(d, "storageGiven", "given");
                String notUsed = first(d, "storageGivenNotUsed", "notUsed");

                parent.addView(driveAdminCard(name, system, userRoot, size, model, temp, status, used, free, given, notUsed));
            }
        } catch (Exception e) {
            parent.addView(card("Physical Drives", small(pretty(text))));
        }
    }

    private View driveAdminCard(final String name, String system, String userRoot, String size, String model, String temp, String status, String used, String free, String given, String notUsed) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackground(round(Color.rgb(20, 8, 7), dp(16), Color.rgb(70, 35, 20), 1));

        TextView title = new TextView(this);
        title.setText(emptyDash(name));
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView details = small(
                "System: " + emptyDash(system) +
                "\nUser root: " + emptyDash(userRoot) +
                "\nSize: " + emptyDash(size) +
                "\nModel: " + emptyDash(model) +
                "\nTemperature: " + emptyDash(temp) +
                "\nStatus: " + emptyDash(status) +
                "\nDrive Used / Total: " + emptyDash(used) +
                "\nDrive Free: " + emptyDash(free) +
                "\nStorage Given: " + emptyDash(given) +
                "\nStorage Given Not Used: " + emptyDash(notUsed)
        );

        final EditText newName = adminInput("New drive name");
        Button change = button("Change Name", YELLOW, Color.BLACK);
        change.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                adminPost("/api/admin/change-drive-name",
                        "oldName=" + enc(name) + "&newName=" + enc(newName.getText().toString()),
                        adminToastReload("Drive name changed"));
            }
        });

        c.addView(title);
        c.addView(details);
        c.addView(newName, inputLp());
        c.addView(change, lp());

        return c;
    }

    private JSONArray extractArray(String text, String[] keys) {
        try {
            String t = text.trim();
            if (t.startsWith("[")) return new JSONArray(t);

            JSONObject obj = new JSONObject(t);
            for (String k : keys) {
                if (obj.has(k) && obj.get(k) instanceof JSONArray) return obj.getJSONArray(k);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private EditText adminInput(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(8, 8, 8), dp(12), Color.rgb(150, 90, 0), 1));
        return e;
    }

    private LinearLayout.LayoutParams inputLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(6), 0, dp(6));
        return lp;
    }

    private TextCallback adminToastReload(final String okMessage) {
        return new TextCallback() {
            @Override public void ok(String text) {
                toast(okMessage);
                showAdmin();
            }

            @Override public void fail(String err) {
                toast("Admin action failed: " + err);
            }
        };
    }

    private void adminPost(final String endpoint, final String body, final TextCallback cb) {
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(abs(endpoint)).openConnection();
                    applySavedCookie(c);
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    c.setDoInput(true);
                    c.setDoOutput(true);
                    c.setUseCaches(false);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    DataOutputStream out = new DataOutputStream(c.getOutputStream());
                    out.writeBytes(body);
                    out.flush();
                    out.close();

                    int code = c.getResponseCode();
                    saveCookieFromResponse(c);
                    final String response = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());

                    if (code >= 200 && code < 300) {
                        ui.post(new Runnable() {
                            @Override public void run() { cb.ok(response); }
                        });
                    } else {
                        ui.post(new Runnable() {
                            @Override public void run() { cb.fail(response); }
                        });
                    }
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() { cb.fail(e.getMessage()); }
                    });
                }
            }
        });
    }

    private String enc(String s) {
        try {
            if (s == null) s = "";
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String emptyDash(String s) {
        if (s == null || s.trim().length() == 0 || "null".equalsIgnoreCase(s.trim())) return "—";
        return s.trim();
    }


    private void showSettings() {
        LinearLayout box = base("Settings");

        EditText input = new EditText(this);
        input.setText(baseUrl == null ? "" : baseUrl);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("http://192.168.1.15:3000");
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(round(CARD2, dp(18), YELLOW, 1));

        box.addView(small("Server URL"));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(54)));

        Button save = button("Save", YELLOW, Color.BLACK);
        save.setOnClickListener(v -> {
            String u = input.getText().toString().trim();
            if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            if (!u.startsWith("http")) {
                toast("Use full URL like http://192.168.1.15:3000");
                return;
            }
            baseUrl = u;
            prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
            toast("Saved");
            showHome();
        });
        box.addView(save, lp());

        Button setup = button("Reconnect Setup", RED, Color.WHITE);
        setup.setOnClickListener(v -> showSetupDialog());
        box.addView(setup, lp());

        box.addView(card("App Info", small("Native UI enabled\nWebView removed\nBackend: " + baseUrl)));

        setScreen(scroll(box));
    }

    private void getText(String endpoint, TextCallback cb) {
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(abs(endpoint)).openConnection();
                c.setConnectTimeout(9000);
                c.setReadTimeout(15000);
                c.setRequestMethod("GET");
                int code = c.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String body = read(is);
                if (code >= 200 && code < 300) ui.post(() -> cb.ok(body));
                else ui.post(() -> cb.fail("HTTP " + code + "\n" + body));
            } catch (Exception e) {
                ui.post(() -> cb.fail(e.getMessage()));
            }
        });
    }

    private void postMultipartSync(String endpoint, Uri fileUri, String fileName) throws Exception {
        String boundary = "FCBoundary" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(abs(endpoint)).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoInput(true);
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setRequestMethod("POST");
        c.setRequestProperty("Connection", "Keep-Alive");
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        applySavedCookie(c);

        String mime = getContentResolver().getType(fileUri);
        if (mime == null) mime = guessMime(fileName);

        DataOutputStream out = new DataOutputStream(c.getOutputStream());
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + clean(fileName) + "\"\r\n");
        out.writeBytes("Content-Type: " + mime + "\r\n\r\n");

        InputStream in = getContentResolver().openInputStream(fileUri);
        if (in == null) throw new Exception("Cannot open file");

        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();

        out.writeBytes("\r\n--" + boundary + "--\r\n");
        out.flush();
        out.close();

        int code = c.getResponseCode();
        saveCookieFromResponse(c);
        String body = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());

        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body);
    }

    private ArrayList<FileItem> parseFiles(String text) {
        ArrayList<FileItem> out = new ArrayList<>();
        try {
            JSONArray arr = null;
            String s = text.trim();

            if (s.startsWith("[")) {
                arr = new JSONArray(s);
            } else {
                JSONObject obj = new JSONObject(s);
                for (String k : new String[]{"files", "items", "data", "images", "media"}) {
                    if (obj.has(k) && obj.get(k) instanceof JSONArray) {
                        arr = obj.getJSONArray(k);
                        break;
                    }
                }
            }

            if (arr == null) return out;

            for (int i = 0; i < arr.length(); i++) {
                Object raw = arr.get(i);
                FileItem it = new FileItem();

                if (raw instanceof JSONObject) {
                    JSONObject o = (JSONObject) raw;
                    it.name = first(o, "name", "filename", "fileName", "originalname", "title");
                    it.url = first(o, "url", "downloadUrl", "download", "path", "src", "href");
                    it.mime = first(o, "mime", "mimeType", "type", "contentType");
                    it.size = o.optLong("size", o.optLong("bytes", 0));
                } else {
                    it.url = String.valueOf(raw);
                    it.name = lastPart(it.url);
                }

                if (it.name == null || it.name.isEmpty()) it.name = lastPart(it.url);
                if (it.name == null || it.name.isEmpty()) it.name = "file-" + (i + 1);
                it.url = abs(it.url);
                out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void loadBitmap(String url, ImageView target) {
        target.setTag(url);
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                InputStream in = new BufferedInputStream(c.getInputStream());
                Bitmap b = BitmapFactory.decodeStream(in);
                in.close();
                ui.post(() -> {
                    if (url.equals(target.getTag())) target.setImageBitmap(b);
                });
            } catch (Exception e) {
                ui.post(() -> target.setImageResource(android.R.drawable.ic_menu_report_image));
            }
        });
    }

    private void openExternal(FileItem item) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(item.url), item.mime == null || item.mime.isEmpty() ? "*/*" : item.mime);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("No app can open this file");
        }
    }

    private void download(FileItem item) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(item.url));
            r.setTitle(item.name);
            r.setDescription("Downloading from Family Cloud");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.name);
            dm.enqueue(r);
            toast("Download started");
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private class GalleryAdapter extends BaseAdapter {
        Context ctx;
        ArrayList<FileItem> items;

        GalleryAdapter(Context c, ArrayList<FileItem> i) {
            ctx = c;
            items = i;
        }

        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View old, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(3), dp(3), dp(3), dp(3));
            cell.setBackground(round(CARD, dp(14), 0, 0));

            ImageView img = new ImageView(ctx);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(Color.BLACK);

            TextView name = new TextView(ctx);
            name.setTextColor(TEXT);
            name.setTextSize(10);
            name.setSingleLine(true);
            name.setPadding(dp(4), dp(4), dp(4), dp(4));

            FileItem it = items.get(pos);
            name.setText(it.name);

            cell.addView(img, new LinearLayout.LayoutParams(-1, dp(118)));
            cell.addView(name, new LinearLayout.LayoutParams(-1, dp(28)));

            if (it.isImage()) loadBitmap(it.url, img);
            else {
                img.setImageResource(it.isVideo() ? android.R.drawable.ic_media_play : android.R.drawable.ic_menu_save);
                img.setPadding(dp(34), dp(34), dp(34), dp(34));
            }

            return cell;
        }
    }

    private static class FileItem {
        String name = "";
        String url = "";
        String mime = "";
        long size = 0;

        boolean isImage() {
            String x = (mime + " " + name + " " + url).toLowerCase(Locale.ROOT);
            return x.contains("image") || x.endsWith(".jpg") || x.endsWith(".jpeg") || x.endsWith(".png") || x.endsWith(".webp") || x.endsWith(".gif");
        }

        boolean isVideo() {
            String x = (mime + " " + name + " " + url).toLowerCase(Locale.ROOT);
            return x.contains("video") || x.endsWith(".mp4") || x.endsWith(".mkv") || x.endsWith(".mov") || x.endsWith(".webm");
        }

        String sizeText() {
            if (size <= 0) return "Unknown";
            double v = size;
            String[] u = {"B", "KB", "MB", "GB"};
            int i = 0;
            while (v >= 1024 && i < u.length - 1) {
                v /= 1024;
                i++;
            }
            return String.format(Locale.ROOT, "%.1f %s", v, u[i]);
        }
    }

    private interface TextCallback {
        void ok(String text);
        void fail(String err);
    }

    private String abs(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;

        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);

        if (s.startsWith("/")) return b + s;
        return b + "/" + s;
    }

    private String pretty(String text) {
        try {
            String s = text.trim();
            if (s.startsWith("{")) return new JSONObject(s).toString(2);
            if (s.startsWith("[")) return new JSONArray(s).toString(2);
            return text;
        } catch (Exception e) {
            return text;
        }
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString().trim();
    }

    private String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v)) return v.trim();
        }
        return "";
    }

    private String lastPart(String s) {
        if (s == null) return "";
        int q = s.indexOf("?");
        if (q >= 0) s = s.substring(0, q);
        int slash = s.lastIndexOf("/");
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    private String getDisplayName(Uri uri) {
        String r = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) r = c.getString(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (r == null) r = uri.getLastPathSegment();
        if (r == null || r.isEmpty()) r = "upload-file";
        return r;
    }

    private String guessMime(String f) {
        String ext = "";
        int dot = f.lastIndexOf(".");
        if (dot >= 0) ext = f.substring(dot + 1).toLowerCase(Locale.ROOT);
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "application/octet-stream" : m;
    }

    private String clean(String s) {
        return s == null ? "file" : s.replace("\"", "").replace("\n", "").replace("\r", "");
    }

    private String joinLines(ArrayList<String> x) {
        StringBuilder sb = new StringBuilder();
        for (String s : x) sb.append(s).append("\n");
        return sb.toString();
    }

    private String formatMillis(long ms) {
        long sec = Math.max(0, ms / 1000);
        long min = sec / 60;
        sec %= 60;
        return min > 0 ? min + " min " + sec + " sec" : sec + " sec";
    }

    private TextView small(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(14);
        t.setLineSpacing(2, 1.05f);
        t.setTextIsSelectable(true);
        return t;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View card(String heading, View body) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, dp(22), 0, 0));
        c.addView(title(heading));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, dp(8), 0, 0);
        c.addView(body, bp);
        c.setLayoutParams(lp());
        return c;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(round(bg, dp(16), 0, 0));
        return b;
    }

    private GradientDrawable round(int color, int radius, int stroke, int width) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (width > 0) g.setStroke(dp(width), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
