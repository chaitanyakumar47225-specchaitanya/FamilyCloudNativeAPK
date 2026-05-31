package com.familycloud.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import java.util.ArrayList;

public class AdminActivity extends Activity {
    private static final String ADMIN_PASSWORD = "8757893577";

    private LinearLayout root;
    private String baseUrl = "";
    private String token = "";
    private String adminCode = "";

    private JSONArray users = new JSONArray();
    private JSONArray drives = new JSONArray();

    private Spinner userSpinner;
    private Spinner addDriveSpinner;
    private Spinner addTypeSpinner;
    private Spinner moveDriveSpinner;
    private Spinner editTypeSpinner;
    private Spinner statusSpinner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        baseUrl = cleanUrl(prefs.getString(MainActivity.KEY_BASE_URL, ""));
        token = prefs.getString(MainActivity.KEY_TOKEN, "");

        showLock();
    }

    private void showLock() {
        base();

        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = text("Admin Login", 28, Color.rgb(255, 196, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        EditText pass = input("Admin password", "");
        pass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        Button unlock = primary("Unlock Admin");
        unlock.setOnClickListener(v -> {
            if (!ADMIN_PASSWORD.equals(pass.getText().toString().trim())) {
                toast("Wrong admin password");
                return;
            }
            adminCode = ADMIN_PASSWORD;
            loadAdmin();
        });

        Button back = ghost("Back");
        back.setOnClickListener(v -> finish());

        card.addView(title);
        card.addView(text("Enter password to manage users and drives.", 15, Color.LTGRAY), full());
        card.addView(pass, full());
        card.addView(unlock, full());
        card.addView(back, full());

        root.addView(card, full());
    }

    private void loadAdmin() {
        base();
        root.addView(text("Loading admin panel...", 18, Color.rgb(255, 196, 0)), full());

        new Thread(() -> {
            try {
                JSONObject overview = get("/api/native/admin/overview-v2?code=" + enc(adminCode));
                JSONObject usersObj = get("/api/native/admin/users-full-v2?code=" + enc(adminCode));
                JSONObject drivesObj = get("/api/native/admin/drives-full-v2?code=" + enc(adminCode));

                users = usersObj.optJSONArray("users");
                drives = drivesObj.optJSONArray("drives");
                if (users == null) users = new JSONArray();
                if (drives == null) drives = new JSONArray();

                runOnUiThread(() -> showPanel(overview));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    base();
                    root.addView(error("Admin load failed: " + e.getMessage()), full());
                    Button retry = primary("Retry");
                    retry.setOnClickListener(v -> loadAdmin());
                    root.addView(retry, full());
                    Button back = ghost("Back");
                    back.setOnClickListener(v -> finish());
                    root.addView(back, full());
                });
            }
        }).start();
    }

    private void showPanel(JSONObject overview) {
        base();

        TextView head = text("Admin Panel", 28, Color.rgb(255, 196, 0));
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setGravity(Gravity.CENTER);
        root.addView(head, full());

        LinearLayout overviewCard = card();
        overviewCard.addView(text("Total users: " + overview.optInt("totalUsers"), 16, Color.WHITE));
        overviewCard.addView(text("Connected devices: " + overview.optInt("connectedDeviceCount", overview.optInt("deviceCount", 0)), 16, Color.rgb(255, 196, 0)));
        overviewCard.addView(text("Storage used by users: " + overview.optString("storageUsedText"), 16, Color.WHITE));
        overviewCard.addView(text("Storage given to users: " + overview.optString("storageGivenText"), 16, Color.WHITE));
        overviewCard.addView(text("Storage given but not used: " + overview.optString("storageGivenNotUsedText"), 16, Color.rgb(255, 196, 0)));
        root.addView(overviewCard, full());

        addUserSection();
        userControlSection();
        drivesSection();

        Button refresh = primary("Refresh Admin Data");
        refresh.setOnClickListener(v -> loadAdmin());

        Button back = ghost("Back");
        back.setOnClickListener(v -> finish());

        root.addView(refresh, full());
        root.addView(back, full());
    }

    private void addUserSection() {
        LinearLayout c = card();
        c.addView(section("Add New User"));

        EditText email = input("User email", "");
        EditText password = input("Password", "");
        EditText storage = input("Allowed storage in GB", "10");

        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        storage.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        addDriveSpinner = spinner(driveLabels());
        addTypeSpinner = spinner(new String[]{"user", "admin"});

        Button add = primary("Add User");
        add.setOnClickListener(v -> {
            try {
                JSONObject b = new JSONObject();
                b.put("code", adminCode);
                b.put("email", email.getText().toString().trim());
                b.put("password", password.getText().toString());
                b.put("allowedGB", storage.getText().toString());
                b.put("driveId", selectedDriveId(addDriveSpinner));
                b.put("userType", addTypeSpinner.getSelectedItem().toString());
                postAsync("/api/native/admin/add-user-full-v2", b, "User added");
            } catch (Exception e) {
                toast(e.getMessage());
            }
        });

        c.addView(email, full());
        c.addView(password, full());
        c.addView(storage, full());
        c.addView(label("Select Drive"));
        c.addView(addDriveSpinner, full());
        c.addView(label("User Type"));
        c.addView(addTypeSpinner, full());
        c.addView(add, full());

        root.addView(c, full());
    }

    private void userControlSection() {
        LinearLayout c = card();
        c.addView(section("Users"));

        userSpinner = spinner(userLabels());
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        Button load = primary("Load Selected User");
        load.setOnClickListener(v -> {
            details.removeAllViews();

            JSONObject u = selectedUser();
            if (u == null) {
                details.addView(error("No user selected"));
                return;
            }

            details.addView(text("Email: " + u.optString("email"), 15, Color.WHITE));
            details.addView(text("Space given: " + u.optString("allowedText"), 15, Color.WHITE));
            details.addView(text("Used: " + u.optString("usedText"), 15, Color.WHITE));
            details.addView(text("Disk: " + u.optString("driveNickname", u.optString("driveId")), 15, Color.rgb(255, 196, 0)));
            details.addView(text("User type: " + u.optString("userType"), 15, Color.WHITE));
            details.addView(text("Status: " + u.optString("status"), 15, u.optString("status").equals("blocked") ? Color.RED : Color.GREEN));

            EditText newPassword = input("New password, blank = unchanged", "");
            EditText newStorage = input("New storage GB, not below used", String.valueOf(round(u.optDouble("allowedGB", 0))));
            newStorage.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            editTypeSpinner = spinner(new String[]{"user", "admin"});
            editTypeSpinner.setSelection(u.optString("userType", "user").equals("admin") ? 1 : 0);

            statusSpinner = spinner(new String[]{"active", "blocked"});
            statusSpinner.setSelection(u.optString("status", "active").equals("blocked") ? 1 : 0);

            moveDriveSpinner = spinner(driveLabels());
            setDriveSpinner(moveDriveSpinner, u.optString("driveId"));

            Button save = primary("Save User Changes");
            save.setOnClickListener(x -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("code", adminCode);
                    b.put("email", u.optString("email"));
                    b.put("password", newPassword.getText().toString());
                    b.put("allowedGB", newStorage.getText().toString());
                    b.put("userType", editTypeSpinner.getSelectedItem().toString());
                    b.put("status", statusSpinner.getSelectedItem().toString());
                    postAsync("/api/native/admin/update-user-full-v2", b, "User updated");
                } catch (Exception e) {
                    toast(e.getMessage());
                }
            });

            Button move = ghost("Move User To Selected Drive");
            move.setOnClickListener(x -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("code", adminCode);
                    b.put("email", u.optString("email"));
                    b.put("driveId", selectedDriveId(moveDriveSpinner));
                    postAsync("/api/native/admin/move-user-full-v2", b, "User moved with files");
                } catch (Exception e) {
                    toast(e.getMessage());
                }
            });

            details.addView(newPassword, full());
            details.addView(newStorage, full());
            details.addView(label("User Type"));
            details.addView(editTypeSpinner, full());
            details.addView(label("Status"));
            details.addView(statusSpinner, full());
            details.addView(label("Move to Drive"));
            details.addView(moveDriveSpinner, full());
            details.addView(save, full());
            details.addView(move, full());
        });

        c.addView(userSpinner, full());
        c.addView(load, full());
        c.addView(details, full());

        root.addView(c, full());
    }

    private void drivesSection() {
        LinearLayout c = card();
        c.addView(section("Physical Drives"));

        for (int i = 0; i < drives.length(); i++) {
            JSONObject d = drives.optJSONObject(i);
            if (d == null) continue;

            LinearLayout box = smallBox();

            box.addView(text("System: " + d.optString("path"), 15, Color.WHITE));
            box.addView(text("Nickname: " + d.optString("nickname"), 15, Color.rgb(255, 196, 0)));
            box.addView(text("User root: " + d.optString("userRoot"), 14, Color.LTGRAY));
            box.addView(text("Size: " + d.optString("sizeText"), 14, Color.WHITE));
            box.addView(text("Model: " + d.optString("model"), 14, Color.WHITE));
            box.addView(text("Temperature: " + d.optString("temperature"), 14, Color.WHITE));
            box.addView(text("Status: " + d.optString("status"), 14, Color.rgb(255, 196, 0)));
            box.addView(text("Drive Used / Total: " + d.optString("usedText") + " / " + d.optString("sizeText"), 14, Color.WHITE));
            box.addView(text("Drive Free: " + d.optString("freeText"), 14, Color.WHITE));
            box.addView(text("Storage Given: " + d.optString("storageGivenText"), 14, Color.WHITE));
            box.addView(text("Storage Given Not Used: " + d.optString("storageGivenNotUsedText"), 14, Color.WHITE));
            box.addView(text("Assigned Users: " + d.optInt("assignedUsers"), 14, Color.WHITE));

            EditText nick = input("Change drive nickname", d.optString("nickname"));
            Button save = primary("Save Drive Nickname");

            save.setOnClickListener(v -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("code", adminCode);
                    b.put("driveId", d.optString("id"));
                    b.put("nickname", nick.getText().toString().trim());
                    postAsync("/api/native/admin/drive-nickname-full-v2", b, "Drive nickname saved");
                } catch (Exception e) {
                    toast(e.getMessage());
                }
            });

            box.addView(nick, full());
            box.addView(save, full());
            c.addView(box, full());
        }

        root.addView(c, full());
    }

    private void postAsync(String endpoint, JSONObject body, String success) {
        new Thread(() -> {
            try {
                JSONObject r = post(endpoint, body);
                if (!r.optBoolean("ok", true)) throw new Exception(r.optString("error", "failed"));
                runOnUiThread(() -> {
                    toast(success);
                    loadAdmin();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Failed: " + e.getMessage()));
            }
        }).start();
    }

    private JSONObject selectedUser() {
        int pos = userSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= users.length()) return null;
        return users.optJSONObject(pos);
    }

    private ArrayList<String> userLabels() {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u != null) out.add(u.optString("email"));
        }
        if (out.isEmpty()) out.add("No users returned from server");
        return out;
    }

    private ArrayList<String> driveLabels() {
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < drives.length(); i++) {
            JSONObject d = drives.optJSONObject(i);
            if (d != null) out.add(d.optString("nickname") + " | " + d.optString("id"));
        }
        if (out.isEmpty()) out.add("No drives returned from server");
        return out;
    }

    private String selectedDriveId(Spinner sp) {
        String v = sp.getSelectedItem() == null ? "" : sp.getSelectedItem().toString();
        int idx = v.lastIndexOf(" | ");
        return idx >= 0 ? v.substring(idx + 3) : v;
    }

    private void setDriveSpinner(Spinner sp, String driveId) {
        for (int i = 0; i < sp.getCount(); i++) {
            if (sp.getItemAtPosition(i).toString().endsWith(" | " + driveId)) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private Spinner spinner(ArrayList<String> items) {
    Spinner sp = new Spinner(this);
    ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(15);
            tv.setPadding(dp(12), dp(12), dp(12), dp(12));
            tv.setBackgroundColor(Color.rgb(24, 24, 24));
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
            tv.setTextColor(Color.rgb(255, 196, 0));
            tv.setTextSize(16);
            tv.setPadding(dp(14), dp(16), dp(14), dp(16));
            tv.setBackgroundColor(Color.rgb(10, 10, 10));
            return tv;
        }
    };
    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp.setAdapter(ad);
    sp.setPopupBackgroundDrawable(bg(Color.rgb(10, 10, 10), Color.rgb(255, 196, 0)));
    return sp;
}

private Spinner spinner(String[] items) {
    ArrayList<String> list = new ArrayList<>();
    for (String item : items) list.add(item);
    return spinner(list);
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

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private String cleanUrl(String s) {
        s = s == null ? "" : s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void base() {
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);

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

    private LinearLayout smallBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(bg(Color.rgb(23, 23, 23), Color.rgb(50, 50, 50)));
        return box;
    }

    private TextView section(String s) {
        TextView t = text(s, 21, Color.rgb(255, 196, 0));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView label(String s) {
        return text(s, 14, Color.LTGRAY);
    }

    private TextView error(String s) {
        return text(s, 15, Color.rgb(255, 100, 100));
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
