package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The news sources (NewsFeed): turn the row on or off, pick the built-in feeds of the mix, and add
 * any other RSS / Atom URL. Every change saves at once and drops the cache, so the Home behind this
 * screen fetches the owner's mix again; RESULT_OK on the way out makes MainActivity recreate().
 */
public final class NewsSettingsActivity extends Activity {
    private SharedPreferences choices;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_news_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        list = findViewById(R.id.list);
        fill();
    }

    private void fill() {
        list.removeAllViews();
        row(getString(R.string.news_enabled,
                getString(NewsFeed.enabled(choices) ? R.string.on : R.string.off)), () -> {
            put("news_enabled", !NewsFeed.enabled(choices));
        });
        boolean idle = choices.getBoolean("news_idle", true);
        row(getString(R.string.news_idle_enabled,
                getString(idle ? R.string.on : R.string.off)), () -> {
            choices.edit().putBoolean("news_idle", !idle).apply();
            setResult(RESULT_OK);
            fill();
        });
        for (NewsFeed.Source s : NewsFeed.catalog()) {
            boolean on = NewsFeed.sourceOn(choices, s);
            row(getString(s.nameRes) + ": " + getString(on ? R.string.on : R.string.off), () -> {
                put("news_source_" + s.key, !on);
            });
        }
        String custom = NewsFeed.customUrl(choices);
        row(getString(R.string.news_custom,
                custom.isEmpty() ? getString(R.string.news_custom_none) : custom), this::editCustom);
        // The first row under the master toggle takes the remote's focus.
        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    /** One custom feed URL on top of the built-in mix; empty removes it. */
    private void editCustom() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String current = NewsFeed.customUrl(choices);
        input.setText(current);
        input.setSelection(current.length());
        input.setHint(R.string.news_custom_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.news_custom_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    choices.edit().putString("news_custom_url", input.getText().toString().trim()).apply();
                    saved();
                    fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Writes one key and drops the cached mix, so the next Home refetches with the new choices. */
    private void put(String key, boolean value) {
        choices.edit().putBoolean(key, value).apply();
        saved();
        fill();
    }

    /** Every change tells the Home behind this screen to rebuild when it closes. */
    private void saved() {
        NewsFeed.clearCache(this);
        setResult(RESULT_OK);
    }

    /** A settings row that runs this choice's editor. */
    private void row(String text, Runnable action) {
        TextView line = new TextView(this, null, 0, R.style.SettingsRow);
        line.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        line.setLayoutParams(lp);
        line.setOnClickListener(v -> action.run());
        list.addView(line);
    }
}
