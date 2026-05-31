package com.familycloud.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class BaseSimpleActivity extends Activity {
    protected LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPage("SRI LADLI", "This page will be rebuilt next.");
    }

    protected void showPage(String title, String subtitle) {
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

        TextView h = text(title, 28, Color.rgb(255, 196, 0));
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setGravity(Gravity.CENTER);

        TextView s = text(subtitle, 16, Color.LTGRAY);
        s.setGravity(Gravity.CENTER);

        Button back = button("Back");
        back.setOnClickListener(v -> finish());

        LinearLayout card = cardBox();
        card.addView(h);
        card.addView(s);
        card.addView(back, fullParams());

        root.addView(card, fullParams());
        setContentView(scroll);
    }

    protected TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(6), dp(5), dp(6), dp(5));
        return t;
    }

    protected Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(52));
        b.setBackground(bg(Color.rgb(255, 196, 0), Color.rgb(255, 196, 0)));
        return b;
    }

    protected LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(bg(Color.rgb(16, 16, 16), Color.rgb(80, 62, 0)));
        return box;
    }

    protected GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), stroke);
        return g;
    }

    protected LinearLayout.LayoutParams fullParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(4), dp(7), dp(4), dp(7));
        return lp;
    }

    protected int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
