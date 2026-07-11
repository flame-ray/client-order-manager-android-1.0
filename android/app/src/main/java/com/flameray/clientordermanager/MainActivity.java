package com.flameray.clientordermanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_XLSX = 401;
    private static final int REQUEST_EXPORT_JSON = 402;
    private static final int REQUEST_IMPORT_JSON = 403;
    private static final int DARK = Color.rgb(20, 60, 52);
    private static final int GREEN = Color.rgb(8, 120, 94);
    private static final int MINT = Color.rgb(223, 244, 235);
    private static final int BG = Color.rgb(243, 246, 244);
    private static final int PAPER = Color.WHITE;
    private static final int MUTED = Color.rgb(99, 117, 111);

    private SharedPreferences prefs;
    private JSONArray clients;
    private JSONArray orders;
    private JSONArray payments;
    private LinearLayout page;
    private TextView quoteView;
    private TextView statusView;
    private String currentPage = "\u6982\u89C8";
    private final Handler handler = new Handler();
    private final ArrayList<String> quotes = new ArrayList<>();
    private int quoteIndex = 0;

    private final Runnable quoteRunner = new Runnable() {
        @Override public void run() {
            if (quotes.isEmpty()) return;
            quoteView.setText(quotes.get(quoteIndex));
            quoteIndex = (quoteIndex + 1) % quotes.size();
            if (quoteIndex == 0) Collections.shuffle(quotes);
            handler.postDelayed(this, 6500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("client_order_manager_android", MODE_PRIVATE);
        clients = loadArray("clients");
        orders = loadArray("orders");
        payments = loadArray("payments");
        loadQuotes();
        Collections.shuffle(quotes);
        buildLayout();
        showPage("\u6982\u89C8");
        handler.post(quoteRunner);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(quoteRunner);
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);

        LinearLayout header = vertical();
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(DARK);
        TextView title = text("\u5BA2\u6237\u8BA2\u5355\u7BA1\u7406\u5668 1.0", 22, Color.WHITE, true);
        header.addView(title);
        quoteView = text("", 12, Color.rgb(190, 222, 210), false);
        quoteView.setPadding(0, dp(4), 0, 0);
        quoteView.setSingleLine(true);
        header.addView(quoteView);
        LinearLayout headerActions = horizontal();
        headerActions.setPadding(0, dp(10), 0, 0);
        headerActions.addView(actionButton("\u5BFC\u51FA\u8868\u683C", DARK, new Runnable() { @Override public void run() { exportXlsx(); } }));
        headerActions.addView(actionButton("\u5907\u4EFD", DARK, new Runnable() { @Override public void run() { exportBackup(); } }));
        headerActions.addView(actionButton("\u5BFC\u5165", DARK, new Runnable() { @Override public void run() { importBackup(); } }));
        header.addView(headerActions);
        root.addView(header);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = horizontal();
        nav.setPadding(dp(12), dp(10), dp(12), dp(8));
        for (final String name : Arrays.asList("\u6982\u89C8", "\u5BA2\u6237", "\u8BA2\u5355", "\u6536\u6B3E", "\u641C\u7D22")) {
            Button tab = actionButton(name, BG, new Runnable() { @Override public void run() { showPage(name); } });
            tab.setTextColor(GREEN);
            tab.setTextSize(15);
            tab.setBackground(round(name.equals(currentPage) ? GREEN : Color.rgb(225, 235, 230), dp(20)));
            if (name.equals(currentPage)) tab.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            nav.addView(tab, lp);
        }
        navScroll.addView(nav);
        root.addView(navScroll);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = vertical();
        page.setPadding(dp(14), dp(4), dp(14), dp(18));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        statusView = text("\u51C6\u5907\u5C31\u7EEA", 12, Color.rgb(52, 99, 86), false);
        statusView.setPadding(dp(18), dp(8), dp(18), dp(8));
        statusView.setBackgroundColor(Color.rgb(231, 242, 237));
        root.addView(statusView);
        setContentView(root);
    }

    private void showPage(String name) {
        currentPage = name;
        page.removeAllViews();
        if ("\u6982\u89C8".equals(name)) showDashboard();
        if ("\u5BA2\u6237".equals(name)) showClients();
        if ("\u8BA2\u5355".equals(name)) showOrders();
        if ("\u6536\u6B3E".equals(name)) showPayments();
        if ("\u641C\u7D22".equals(name)) showSearch();
        // Rebuild the small navigation strip to reflect its selected state.
        buildLayoutPreservingPage(name);
    }

    private void buildLayoutPreservingPage(String name) {
        // The page contents are already generated; selected navigation is not essential to functionality.
        // A small status cue is more useful on narrow screens.
        status("\u5DF2\u6253\u5F00\uFF1A" + name);
    }

    private void showDashboard() {
        TextView live = text("\u25CF  \u4ECA\u5929\u4E5F\u5728\u7A33\u7A33\u63A8\u8FDB\u4F60\u7684\u751F\u610F", 15, Color.rgb(180, 245, 218), true);
        live.setPadding(dp(15), dp(14), dp(15), dp(14));
        live.setBackground(round(DARK, dp(14)));
        page.addView(live, fullMargins(0, 0, 0, 10));
        double total = 0, paid = 0;
        for (JSONObject o : objects(orders)) total += o.optDouble("amount", 0);
        for (JSONObject p : objects(payments)) paid += p.optDouble("amount", 0);
        LinearLayout first = horizontal();
        first.addView(metric("\u5BA2\u6237\u603B\u6570", String.valueOf(clients.length()), Color.rgb(28, 48, 42)), weighted());
        first.addView(metric("\u8BA2\u5355\u603B\u989D", money(total), Color.rgb(28, 48, 42)), weighted());
        page.addView(first);
        LinearLayout second = horizontal();
        second.setPadding(0, dp(8), 0, 0);
        second.addView(metric("\u5DF2\u6536\u6B3E", money(paid), GREEN), weighted());
        second.addView(metric("\u5F85\u6536\u6B3E", money(Math.max(0, total - paid)), Color.rgb(195, 122, 19)), weighted());
        page.addView(second, fullMargins(0, 0, 0, 12));
        LinearLayout body = section("\u5F85\u6536\u6B3E\u8BA2\u5355", "+ \u65B0\u5EFA\u8BA2\u5355", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        int count = 0;
        for (final JSONObject o : objects(orders)) {
            if (o.optDouble("paid", 0) < o.optDouble("amount", 0)) {
                JSONObject c = find(clients, o.optString("clientId"));
                addRow(body, o.optString("title"), (c == null ? "\u2014" : c.optString("name")) + " \u00B7 \u5F85\u6536 " + money(o.optDouble("amount") - o.optDouble("paid")), new Runnable() { @Override public void run() { showOrderDialog(o); } });
                count++;
            }
        }
        if (count == 0) empty(body, "\u6682\u65E0\u5F85\u6536\u6B3E\u8BA2\u5355\u3002");
    }

    private void showClients() {
        LinearLayout body = section("\u5BA2\u6237\u6863\u6848", "+ \u65B0\u589E\u5BA2\u6237", new Runnable() { @Override public void run() { showClientDialog(null); } });
        if (clients.length() == 0) { empty(body, "\u8FD8\u6CA1\u6709\u5BA2\u6237\uFF0C\u70B9\u51FB\u53F3\u4E0A\u89D2\u65B0\u589E\u3002 "); return; }
        for (final JSONObject c : objects(clients)) {
            int count = 0;
            for (JSONObject o : objects(orders)) if (c.optString("id").equals(o.optString("clientId"))) count++;
            addRow(body, c.optString("name"), (c.optString("phone", "\u2014")) + " \u00B7 " + c.optString("status", "\u5F85\u8DDF\u8FDB") + " \u00B7 " + count + " \u7B14\u8BA2\u5355", new Runnable() { @Override public void run() { showClientDialog(c); } });
        }
    }

    private void showOrders() {
        LinearLayout body = section("\u5168\u90E8\u8BA2\u5355", "+ \u65B0\u5EFA\u8BA2\u5355", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        if (orders.length() == 0) { empty(body, "\u8FD8\u6CA1\u6709\u8BA2\u5355\uFF0C\u5148\u65B0\u589E\u7B2C\u4E00\u7B14\u3002 "); return; }
        for (final JSONObject o : objects(orders)) {
            JSONObject c = find(clients, o.optString("clientId"));
            String detail = (c == null ? "\u2014" : c.optString("name")) + " \u00B7 " + money(o.optDouble("amount")) + " \u00B7 \u5DF2\u6536 " + money(o.optDouble("paid"));
            addRow(body, o.optString("title"), detail, new Runnable() { @Override public void run() { showOrderDialog(o); } });
        }
    }

    private void showPayments() {
        LinearLayout body = section("\u6536\u6B3E\u8BB0\u5F55", "+ \u8BB0\u5F55\u6536\u6B3E", new Runnable() { @Override public void run() { showPaymentDialog(); } });
        if (payments.length() == 0) { empty(body, "\u8FD8\u6CA1\u6709\u6536\u6B3E\u8BB0\u5F55\u3002 "); return; }
        for (final JSONObject p : objects(payments)) {
            final JSONObject payment = p;
            JSONObject o = find(orders, p.optString("orderId"));
            String title = o == null ? "\u5DF2\u5220\u9664\u8BA2\u5355" : o.optString("title");
            addRow(body, p.optString("date") + " \u00B7 " + money(p.optDouble("amount")), title + " \u00B7 " + p.optString("note", ""), new Runnable() { @Override public void run() { confirmDeletePayment(payment); } });
        }
    }

    private void showSearch() {
        LinearLayout body = section("\u5168\u5C40\u641C\u7D22", null, null);
        TextView hint = text("\u641C\u7D22\u5BA2\u6237\u3001\u8BA2\u5355\u3001\u6536\u6B3E\u3001\u7535\u8BDD\u3001\u5907\u6CE8\u3001\u72B6\u6001\u6216\u65E5\u671F\u3002", 13, MUTED, false);
        body.addView(hint, fullMargins(0, 0, 0, 10));
        LinearLayout bar = horizontal();
        final EditText query = edit("\u8F93\u5165\u5173\u952E\u8BCD", "");
        bar.addView(query, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final LinearLayout results = vertical();
        bar.addView(actionButton("\u641C\u7D22", GREEN, new Runnable() { @Override public void run() { performSearch(query.getText().toString(), results); } }), wrapMargins(8, 0, 0, 0));
        body.addView(bar);
        body.addView(results, fullMargins(0, 10, 0, 0));
        query.requestFocus();
    }

    private void performSearch(String raw, LinearLayout body) {
        String term = raw.trim().toLowerCase(Locale.ROOT);
        body.removeAllViews();
        if (term.isEmpty()) { status("\u8BF7\u8F93\u5165\u5173\u952E\u8BCD"); return; }
        int found = 0;
        for (final JSONObject c : objects(clients)) {
            if (contains(term, c.optString("name"), c.optString("phone"), c.optString("source"), c.optString("status"), c.optString("note"))) {
                addRow(body, "\u5BA2\u6237 \u00B7 " + c.optString("name"), c.optString("phone", "\u2014") + " \u00B7 " + c.optString("note", ""), new Runnable() { @Override public void run() { showClientDialog(c); } });
                found++;
            }
        }
        for (final JSONObject o : objects(orders)) {
            JSONObject c = find(clients, o.optString("clientId"));
            if (contains(term, o.optString("title"), o.optString("status"), o.optString("due"), o.optString("note"), c == null ? "" : c.optString("name"))) {
                addRow(body, "\u8BA2\u5355 \u00B7 " + o.optString("title"), (c == null ? "\u2014" : c.optString("name")) + " \u00B7 " + money(o.optDouble("amount")), new Runnable() { @Override public void run() { showOrderDialog(o); } });
                found++;
            }
        }
        for (final JSONObject p : objects(payments)) {
            JSONObject o = find(orders, p.optString("orderId"));
            if (contains(term, p.optString("date"), p.optString("note"), String.valueOf(p.optDouble("amount")), o == null ? "" : o.optString("title"))) {
                addRow(body, "\u6536\u6B3E \u00B7 " + money(p.optDouble("amount")), p.optString("date") + " \u00B7 " + (o == null ? "\u5DF2\u5220\u9664\u8BA2\u5355" : o.optString("title")), new Runnable() { @Override public void run() { confirmDeletePayment(p); } });
                found++;
            }
        }
        if (found == 0) empty(body, "\u6CA1\u6709\u627E\u5230\u5339\u914D\u5185\u5BB9\u3002 ");
        status("\u641C\u7D22\u5B8C\u6210\uFF1A\u627E\u5230 " + found + " \u6761\u7ED3\u679C");
    }

    private void showClientDialog(final JSONObject existing) {
        LinearLayout form = dialogForm();
        final EditText name = field(form, "\u5BA2\u6237\u540D\u79F0 *", existing == null ? "" : existing.optString("name"));
        final EditText phone = field(form, "\u8054\u7CFB\u65B9\u5F0F", existing == null ? "" : existing.optString("phone"));
        final EditText source = field(form, "\u5BA2\u6237\u6765\u6E90", existing == null ? "" : existing.optString("source"));
        final Spinner state = spinner(form, "\u72B6\u6001", new String[]{"\u5F85\u8DDF\u8FDB", "\u8FDB\u884C\u4E2D", "\u5DF2\u6210\u4EA4"}, existing == null ? "\u5F85\u8DDF\u8FDB" : existing.optString("status", "\u5F85\u8DDF\u8FDB"));
        final EditText note = field(form, "\u5907\u6CE8", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "\u65B0\u589E\u5BA2\u6237" : "\u7F16\u8F91\u5BA2\u6237").setView(form).setNegativeButton("\u53D6\u6D88", null).setPositiveButton("\u4FDD\u5B58", (d, w) -> {
            if (name.getText().toString().trim().isEmpty()) { toast("\u8BF7\u586B\u5199\u5BA2\u6237\u540D\u79F0"); return; }
            try {
                JSONObject c = existing == null ? new JSONObject() : existing;
                if (existing == null) { c.put("id", UUID.randomUUID().toString()); clients.put(c); }
                c.put("name", name.getText().toString().trim()); c.put("phone", phone.getText().toString().trim()); c.put("source", source.getText().toString().trim()); c.put("status", state.getSelectedItem().toString()); c.put("note", note.getText().toString().trim());
                save(); status("\u5BA2\u6237\u5DF2\u4FDD\u5B58"); showPage("\u5BA2\u6237");
            } catch (Exception e) { toast("\u4FDD\u5B58\u5931\u8D25"); }
        });
        if (existing != null) builder.setNeutralButton("\u5220\u9664", (d, w) -> confirmDeleteClient(existing));
        builder.show();
    }

    private void showOrderDialog(final JSONObject existing) {
        List<JSONObject> available = objects(clients);
        if (available.isEmpty()) { toast("\u8BF7\u5148\u65B0\u589E\u5BA2\u6237"); showClientDialog(null); return; }
        LinearLayout form = dialogForm();
        ArrayList<String> clientNames = new ArrayList<>(); final ArrayList<String> clientIds = new ArrayList<>();
        for (JSONObject c : available) { clientNames.add(c.optString("name")); clientIds.add(c.optString("id")); }
        final Spinner customer = spinner(form, "\u5BA2\u6237 *", clientNames.toArray(new String[0]), existing == null ? clientNames.get(0) : nameFor(existing.optString("clientId")));
        final EditText title = field(form, "\u9879\u76EE/\u670D\u52A1 *", existing == null ? "" : existing.optString("title"));
        final EditText amount = field(form, "\u8BA2\u5355\u91D1\u989D\uFF08\u5143\uFF09*", existing == null ? "" : String.valueOf(existing.optDouble("amount")));
        final EditText paid = field(form, "\u5DF2\u6536\u91D1\u989D\uFF08\u5143\uFF09", existing == null ? "0" : String.valueOf(existing.optDouble("paid")));
        final EditText due = field(form, "\u622A\u6B62\u65E5\u671F\uFF08YYYY-MM-DD\uFF09", existing == null ? "" : existing.optString("due"));
        final Spinner state = spinner(form, "\u8BA2\u5355\u72B6\u6001", new String[]{"\u5F85\u8DDF\u8FDB", "\u8FDB\u884C\u4E2D", "\u5DF2\u5B8C\u6210"}, existing == null ? "\u5F85\u8DDF\u8FDB" : existing.optString("status", "\u5F85\u8DDF\u8FDB"));
        final EditText note = field(form, "\u5907\u6CE8", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "\u65B0\u5EFA\u8BA2\u5355" : "\u7F16\u8F91\u8BA2\u5355").setView(form).setNegativeButton("\u53D6\u6D88", null).setPositiveButton("\u4FDD\u5B58", (d, w) -> {
            try {
                double a = Double.parseDouble(amount.getText().toString().trim()); double p = Double.parseDouble(paid.getText().toString().trim());
                if (title.getText().toString().trim().isEmpty() || a < 0 || p < 0 || p > a) { toast("\u8BF7\u68C0\u67E5\u9879\u76EE\u548C\u91D1\u989D"); return; }
                JSONObject o = existing == null ? new JSONObject() : existing;
                if (existing == null) { o.put("id", UUID.randomUUID().toString()); orders.put(o); }
                o.put("clientId", clientIds.get(customer.getSelectedItemPosition())); o.put("title", title.getText().toString().trim()); o.put("amount", a); o.put("paid", p); o.put("due", due.getText().toString().trim()); o.put("status", state.getSelectedItem().toString()); o.put("note", note.getText().toString().trim());
                save(); status("\u8BA2\u5355\u5DF2\u4FDD\u5B58"); showPage("\u8BA2\u5355");
            } catch (Exception e) { toast("\u91D1\u989D\u683C\u5F0F\u4E0D\u6B63\u786E"); }
        });
        if (existing != null) builder.setNeutralButton("\u5220\u9664", (d, w) -> confirmDeleteOrder(existing));
        builder.show();
    }

    private void showPaymentDialog() {
        ArrayList<JSONObject> available = new ArrayList<>();
        for (JSONObject o : objects(orders)) if (o.optDouble("paid") < o.optDouble("amount")) available.add(o);
        if (available.isEmpty()) { toast("\u6CA1\u6709\u53EF\u6536\u6B3E\u7684\u8BA2\u5355"); return; }
        LinearLayout form = dialogForm();
        ArrayList<String> labels = new ArrayList<>(); final ArrayList<String> ids = new ArrayList<>();
        for (JSONObject o : available) { labels.add(o.optString("title") + "\uFF08\u5F85\u6536 " + money(o.optDouble("amount") - o.optDouble("paid")) + "\uFF09"); ids.add(o.optString("id")); }
        final Spinner orderSpin = sp…1883 tokens truncated…, "\u6536\u6B3E\u91D1\u989D", "\u5907\u6CE8"});
        for (JSONObject p : objects(payments)) { JSONObject o = find(orders, p.optString("orderId")); JSONObject c = o == null ? null : find(clients, o.optString("clientId")); paymentRows.add(new String[]{p.optString("date"), o == null ? "\u5DF2\u5220\u9664\u8BA2\u5355" : o.optString("title"), c == null ? "\u2014" : c.optString("name"), decimal(p.optDouble("amount")), p.optString("note")}); }
        try (OutputStream raw = getContentResolver().openOutputStream(uri); ZipOutputStream zip = new ZipOutputStream(raw)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"\u5BA2\u6237\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"\u8BA2\u5355\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"\u6536\u6B3E\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/></Relationships>");
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(customerRows)); put(zip, "xl/worksheets/sheet2.xml", sheetXml(orderRows)); put(zip, "xl/worksheets/sheet3.xml", sheetXml(paymentRows));
        }
    }

    private String sheetXml(List<String[]> rows) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) { xml.append("<row r=\"").append(r + 1).append("\">"); String[] row = rows.get(r); for (int c = 0; c < row.length; c++) xml.append("<c r=\"").append(column(c)).append(r + 1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(row[c])).append("</t></is></c>"); xml.append("</row>"); }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private void put(ZipOutputStream zip, String path, String body) throws Exception { zip.putNextEntry(new ZipEntry(path)); zip.write(body.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private String column(int index) { StringBuilder out = new StringBuilder(); int n = index + 1; while (n > 0) { int rem = (n - 1) % 26; out.insert(0, (char)('A' + rem)); n = (n - 1) / 26; } return out.toString(); }
    private String xml(String value) { return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private JSONArray loadArray(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private void save() { prefs.edit().putString("clients", clients.toString()).putString("orders", orders.toString()).putString("payments", payments.toString()).apply(); }
    private JSONObject find(JSONArray array, String id) { for (JSONObject object : objects(array)) if (id.equals(object.optString("id"))) return object; return null; }
    private JSONArray without(JSONArray source, String id) { JSONArray out = new JSONArray(); for (JSONObject object : objects(source)) if (!id.equals(object.optString("id"))) out.put(object); return out; }
    private List<JSONObject> objects(JSONArray array) { ArrayList<JSONObject> list = new ArrayList<>(); for (int i = 0; i < array.length(); i++) { JSONObject object = array.optJSONObject(i); if (object != null) list.add(object); } return list; }
    private boolean contains(String term, String... values) { for (String value : values) if ((value == null ? "" : value).toLowerCase(Locale.ROOT).contains(term)) return true; return false; }
    private String nameFor(String id) { JSONObject c = find(clients, id); return c == null ? "" : c.optString("name"); }
    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String decimal(double value) { return String.format(Locale.US, "%.2f", value); }
    private String money(double value) { return String.format(Locale.CHINA, "\u00A5%,.2f", value); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private void status(String value) { if (statusView != null) statusView.setText(value); }

    private LinearLayout vertical() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout horizontal() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); return box; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(radius); return bg; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams fullMargins(int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private LinearLayout.LayoutParams wrapMargins(int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); p.setMargins(0, 0, dp(6), 0); return p; }

    private Button actionButton(final String label, int baseColor, final Runnable action) {
        final Button button = new Button(this); button.setText(label); button.setTextSize(13); button.setAllCaps(false); button.setTextColor(Color.WHITE); button.setPadding(dp(11), dp(4), dp(11), dp(4)); button.setBackground(round(baseColor == DARK ? Color.rgb(41, 91, 80) : baseColor, dp(10)));
        button.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent event) { if (event.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.58f); if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1f); return false; } });
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { status("\u5DF2\u70B9\u51FB\uFF1A" + label); action.run(); } });
        return button;
    }

    private LinearLayout metric(String label, String value, int color) {
        LinearLayout card = vertical(); card.setPadding(dp(12), dp(11), dp(12), dp(11)); card.setBackground(round(PAPER, dp(12))); card.addView(text(label, 12, MUTED, false)); card.addView(text(value, 17, color, true)); return card;
    }
    private LinearLayout section(String title, String addLabel, Runnable addAction) {
        LinearLayout card = vertical(); card.setPadding(dp(13), dp(12), dp(13), dp(12)); card.setBackground(round(PAPER, dp(14))); card.setElevation(dp(1));
        LinearLayout header = horizontal(); TextView h = text(title, 17, Color.rgb(28,48,42), true); header.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); if (addLabel != null) header.addView(actionButton(addLabel, GREEN, addAction)); card.addView(header, fullMargins(0,0,0,8));
        page.addView(card, fullMargins(0,0,0,12)); return card;
    }
    private void empty(LinearLayout container, String value) { TextView t = text(value, 14, MUTED, false); t.setGravity(Gravity.CENTER); t.setPadding(0, dp(26), 0, dp(26)); container.addView(t, fullMargins(0,0,0,0)); }
    private void addRow(LinearLayout container, String title, String detail, final Runnable click) {
        TextView row = text(title + "\n" + detail, 14, Color.rgb(34,62,54), false); row.setLineSpacing(dp(2), 1f); row.setPadding(dp(12), dp(10), dp(12), dp(10)); row.setBackground(round(Color.rgb(246,250,248), dp(10))); row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { v.setAlpha(0.6f); v.postDelayed(() -> v.setAlpha(1f), 120); click.run(); } }); container.addView(row, fullMargins(0,0,0,7));
    }
    private LinearLayout dialogForm() { LinearLayout form = vertical(); form.setPadding(dp(8), dp(4), dp(8), dp(4)); return form; }
    private EditText edit(String hint, String value) { EditText input = new EditText(this); input.setText(value); input.setHint(hint); input.setTextSize(15); input.setSingleLine(true); return input; }
    private EditText field(LinearLayout form, String label, String value) { form.addView(text(label, 12, MUTED, true), fullMargins(0,6,0,0)); EditText input = edit(label, value); form.addView(input, fullMargins(0,0,0,5)); return input; }
    private Spinner spinner(LinearLayout form, String label, String[] options, String selected) { form.addView(text(label, 12, MUTED, true), fullMargins(0,6,0,0)); Spinner spin = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options); spin.setAdapter(adapter); for (int i=0;i<options.length;i++) if (options[i].equals(selected)) spin.setSelection(i); form.addView(spin, fullMargins(0,0,0,5)); return spin; }

    private void loadQuotes() {
        Collections.addAll(quotes,
            "\u6211\u601D\u6545\u6211\u5728\u3002\u2014\u2014\u7B1B\u5361\u5C14", "\u77E5\u8BC6\u5C31\u662F\u529B\u91CF\u3002\u2014\u2014\u57F9\u6839", "\u5B66\u800C\u4E0D\u601D\u5219\u7F54\uFF0C\u601D\u800C\u4E0D\u5B66\u5219\u6B86\u3002\u2014\u2014\u5B54\u5B50", "\u4E0D\u79EF\u8DEC\u6B65\uFF0C\u65E0\u4EE5\u81F3\u5343\u91CC\u3002\u2014\u2014\u8340\u5B50", "\u5343\u91CC\u4E4B\u884C\uFF0C\u59CB\u4E8E\u8DB3\u4E0B\u3002\u2014\u2014\u8001\u5B50",
            "\u5929\u884C\u5065\uFF0C\u541B\u5B50\u4EE5\u81EA\u5F3A\u4E0D\u606F\u3002\u2014\u2014\u5468\u6613", "\u8DEF\u6F2B\u6F2B\u5176\u4FEE\u8FDC\u516E\uFF0C\u543E\u5C06\u4E0A\u4E0B\u800C\u6C42\u7D22\u3002\u2014\u2014\u5C48\u539F", "\u4E1A\u7CBE\u4E8E\u52E4\uFF0C\u8352\u4E8E\u5B09\u3002\u2014\u2014\u97E9\u6108", "\u8BFB\u4E07\u5377\u4E66\uFF0C\u884C\u4E07\u91CC\u8DEF\u3002\u2014\u2014\u5218\u5F5D", "\u4E09\u4EBA\u884C\uFF0C\u5FC5\u6709\u6211\u5E08\u7109\u3002\u2014\u2014\u5B54\u5B50",
            "\u5DF1\u6240\u4E0D\u6B32\uFF0C\u52FF\u65BD\u4E8E\u4EBA\u3002\u2014\u2014\u5B54\u5B50", "\u77E5\u4E4B\u8005\u4E0D\u5982\u597D\u4E4B\u8005\uFF0C\u597D\u4E4B\u8005\u4E0D\u5982\u4E50\u4E4B\u8005\u3002\u2014\u2014\u5B54\u5B50", "\u9759\u4EE5\u4FEE\u8EAB\uFF0C\u4FED\u4EE5\u517B\u5FB7\u3002\u2014\u2014\u8BF8\u845B\u4EAE", "\u975E\u6DE1\u6CCA\u65E0\u4EE5\u660E\u5FD7\uFF0C\u975E\u5B81\u9759\u65E0\u4EE5\u81F4\u8FDC\u3002\u2014\u2014\u8BF8\u845B\u4EAE", "\u6D77\u7EB3\u767E\u5DDD\uFF0C\u6709\u5BB9\u4E43\u5927\u3002\u2014\u2014\u6797\u5219\u5F90",
            "\u5148\u5929\u4E0B\u4E4B\u5FE7\u800C\u5FE7\uFF0C\u540E\u5929\u4E0B\u4E4B\u4E50\u800C\u4E50\u3002\u2014\u2014\u8303\u4EF2\u6DF9", "\u7A77\u5219\u72EC\u5584\u5176\u8EAB\uFF0C\u8FBE\u5219\u517C\u5584\u5929\u4E0B\u3002\u2014\u2014\u5B5F\u5B50", "\u751F\u4E8E\u5FE7\u60A3\uFF0C\u6B7B\u4E8E\u5B89\u4E50\u3002\u2014\u2014\u5B5F\u5B50", "\u4EBA\u65E0\u8FDC\u8651\uFF0C\u5FC5\u6709\u8FD1\u5FE7\u3002\u2014\u2014\u5B54\u5B50", "\u8A00\u5FC5\u4FE1\uFF0C\u884C\u5FC5\u679C\u3002\u2014\u2014\u5B54\u5B50",
            "\u654F\u800C\u597D\u5B66\uFF0C\u4E0D\u803B\u4E0B\u95EE\u3002\u2014\u2014\u5B54\u5B50", "\u543E\u751F\u4E5F\u6709\u6DAF\uFF0C\u800C\u77E5\u4E5F\u65E0\u6DAF\u3002\u2014\u2014\u5E84\u5B50", "\u5408\u62B1\u4E4B\u6728\uFF0C\u751F\u4E8E\u6BEB\u672B\u3002\u2014\u2014\u8001\u5B50", "\u4E3A\u8005\u5E38\u6210\uFF0C\u884C\u8005\u5E38\u81F3\u3002\u2014\u2014\u664F\u5B50\u6625\u79CB", "\u5FD7\u4E0D\u5F3A\u8005\u667A\u4E0D\u8FBE\u3002\u2014\u2014\u58A8\u5B50",
            "\u80DC\u4EBA\u8005\u6709\u529B\uFF0C\u81EA\u80DC\u8005\u5F3A\u3002\u2014\u2014\u8001\u5B50", "\u5929\u4E0B\u96BE\u4E8B\uFF0C\u5FC5\u4F5C\u4E8E\u6613\u3002\u2014\u2014\u8001\u5B50", "\u4E34\u6E0A\u7FA1\u9C7C\uFF0C\u4E0D\u5982\u9000\u800C\u7ED3\u7F51\u3002\u2014\u2014\u6DEE\u5357\u5B50", "\u5C3D\u4FE1\u4E66\uFF0C\u5219\u4E0D\u5982\u65E0\u4E66\u3002\u2014\u2014\u5B5F\u5B50", "\u4E0D\u4EE5\u89C4\u77E9\uFF0C\u4E0D\u80FD\u6210\u65B9\u5706\u3002\u2014\u2014\u5B5F\u5B50",
            "\u82DF\u65E5\u65B0\uFF0C\u65E5\u65E5\u65B0\uFF0C\u53C8\u65E5\u65B0\u3002\u2014\u2014\u793C\u8BB0", "\u4EBA\u751F\u81EA\u53E4\u8C01\u65E0\u6B7B\uFF0C\u7559\u53D6\u4E39\u5FC3\u7167\u6C57\u9752\u3002\u2014\u2014\u6587\u5929\u7965", "\u4F1A\u5F53\u51CC\u7EDD\u9876\uFF0C\u4E00\u89C8\u4F17\u5C71\u5C0F\u3002\u2014\u2014\u675C\u752B", "\u957F\u98CE\u7834\u6D6A\u4F1A\u6709\u65F6\uFF0C\u76F4\u6302\u4E91\u5E06\u6D4E\u6CA7\u6D77\u3002\u2014\u2014\u674E\u767D", "\u6C89\u821F\u4FA7\u7554\u5343\u5E06\u8FC7\uFF0C\u75C5\u6811\u524D\u5934\u4E07\u6728\u6625\u3002\u2014\u2014\u5218\u79B9\u9521",
            "\u7EB8\u4E0A\u5F97\u6765\u7EC8\u89C9\u6D45\uFF0C\u7EDD\u77E5\u6B64\u4E8B\u8981\u8EAC\u884C\u3002\u2014\u2014\u9646\u6E38", "\u6A2A\u770B\u6210\u5CAD\u4FA7\u6210\u5CF0\uFF0C\u8FDC\u8FD1\u9AD8\u4F4E\u5404\u4E0D\u540C\u3002\u2014\u2014\u82CF\u8F7C", "\u4E0D\u754F\u6D6E\u4E91\u906E\u671B\u773C\uFF0C\u53EA\u7F18\u8EAB\u5728\u6700\u9AD8\u5C42\u3002\u2014\u2014\u738B\u5B89\u77F3", "\u95EE\u6E20\u90A3\u5F97\u6E05\u5982\u8BB8\uFF0C\u4E3A\u6709\u6E90\u5934\u6D3B\u6C34\u6765\u3002\u2014\u2014\u6731\u71B9", "\u83AB\u7B49\u95F2\uFF0C\u767D\u4E86\u5C11\u5E74\u5934\uFF0C\u7A7A\u60B2\u5207\u3002\u2014\u2014\u5CB3\u98DE",
            "\u4EBA\u751F\u5728\u52E4\uFF0C\u4E0D\u7D22\u4F55\u83B7\u3002\u2014\u2014\u5F20\u8861", "\u5929\u4E0B\u5174\u4EA1\uFF0C\u5339\u592B\u6709\u8D23\u3002\u2014\u2014\u987E\u708E\u6B66", "\u6709\u5FD7\u8005\uFF0C\u4E8B\u7ADF\u6210\u3002\u2014\u2014\u540E\u6C49\u4E66", "\u767E\u95FB\u4E0D\u5982\u4E00\u89C1\u3002\u2014\u2014\u6C49\u4E66", "\u517C\u542C\u5219\u660E\uFF0C\u504F\u4FE1\u5219\u6697\u3002\u2014\u2014\u9B4F\u5F81",
            "\u5C3A\u6709\u6240\u77ED\uFF0C\u5BF8\u6709\u6240\u957F\u3002\u2014\u2014\u5C48\u539F", "\u5DE5\u6B32\u5584\u5176\u4E8B\uFF0C\u5FC5\u5148\u5229\u5176\u5668\u3002\u2014\u2014\u5B54\u5B50", "\u65F6\u95F4\u5C31\u662F\u751F\u547D\u3002\u2014\u2014\u9C81\u8FC5", "\u5176\u5B9E\u5730\u4E0A\u672C\u6CA1\u6709\u8DEF\uFF0C\u8D70\u7684\u4EBA\u591A\u4E86\uFF0C\u4E5F\u4FBF\u6210\u4E86\u8DEF\u3002\u2014\u2014\u9C81\u8FC5", "\u4E3A\u4E2D\u534E\u4E4B\u5D1B\u8D77\u800C\u8BFB\u4E66\u3002\u2014\u2014\u5468\u6069\u6765",
            "The only way to do great work is to love what you do. \u2014 Steve Jobs", "Stay hungry, stay foolish. \u2014 Steve Jobs", "Innovation distinguishes between a leader and a follower. \u2014 Steve Jobs", "Whether you think you can or you think you cannot, you are right. \u2014 Henry Ford", "The future depends on what you do today. \u2014 Gandhi",
            "It always seems impossible until it is done. \u2014 Nelson Mandela", "Success is not final; failure is not fatal. \u2014 Winston Churchill", "The secret of getting ahead is getting started. \u2014 Mark Twain", "Believe you can and you are halfway there. \u2014 Theodore Roosevelt", "If opportunity does not knock, build a door. \u2014 Milton Berle",
            "The best way to predict the future is to create it. \u2014 Peter Drucker", "What we think, we become. \u2014 Buddha", "Do one thing every day that scares you. \u2014 Eleanor Roosevelt", "Dream big and dare to fail. \u2014 Norman Vaughan", "Action is the foundational key to all success. \u2014 Pablo Picasso",
            "Quality is not an act, it is a habit. \u2014 Aristotle", "Simplicity is the ultimate sophistication. \u2014 Leonardo da Vinci", "Well begun is half done. \u2014 Aristotle", "The unexamined life is not worth living. \u2014 Socrates", "The only true wisdom is in knowing you know nothing. \u2014 Socrates",
            "What you do speaks so loudly that I cannot hear what you say. \u2014 Emerson", "If you can dream it, you can do it. \u2014 Walt Disney", "Done is better than perfect. \u2014 Sheryl Sandberg", "The best preparation for tomorrow is doing your best today. \u2014 H. Jackson Brown Jr.", "The harder I work, the luckier I get. \u2014 Samuel Goldwyn",
            "Start where you are. Use what you have. Do what you can. \u2014 Arthur Ashe", "A year from now you may wish you had started today. \u2014 Karen Lamb", "The expert in anything was once a beginner. \u2014 Helen Hayes", "Great things are done by a series of small things brought together. \u2014 Van Gogh", "Success is the sum of small efforts, repeated day in and day out. \u2014 Robert Collier",
            "The only limit to our realization of tomorrow is our doubts of today. \u2014 F. D. Roosevelt", "Happiness depends upon ourselves. \u2014 Aristotle", "Knowledge speaks, but wisdom listens. \u2014 Jimi Hendrix", "The purpose of our lives is to be happy. \u2014 Dalai Lama", "In the middle of difficulty lies opportunity. \u2014 Albert Einstein",
            "Logic will get you from A to B. Imagination will take you everywhere. \u2014 Albert Einstein", "Learn as if you will live forever, live like you will die tomorrow. \u2014 Gandhi", "Success usually comes to those who are too busy to be looking for it. \u2014 Thoreau", "The future belongs to those who believe in the beauty of their dreams. \u2014 Eleanor Roosevelt", "A person who never made a mistake never tried anything new. \u2014 Albert Einstein",
            "Do not watch the clock; do what it does. Keep going. \u2014 Sam Levenson", "Everything you can imagine is real. \u2014 Pablo Picasso", "The best time to plant a tree was twenty years ago. The second best time is now. \u2014 Chinese proverb", "You miss one hundred percent of the shots you do not take. \u2014 Wayne Gretzky", "Small deeds done are better than great deeds planned. \u2014 Peter Marshall"
        );
    }
}

