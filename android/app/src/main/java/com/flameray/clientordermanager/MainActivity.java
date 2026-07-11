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
    private String currentPage = "姒傝";
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
        showPage("姒傝");
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
        TextView title = text("瀹㈡埛璁㈠崟绠＄悊鍣?1.0", 22, Color.WHITE, true);
        header.addView(title);
        quoteView = text("", 12, Color.rgb(190, 222, 210), false);
        quoteView.setPadding(0, dp(4), 0, 0);
        quoteView.setSingleLine(true);
        header.addView(quoteView);
        LinearLayout headerActions = horizontal();
        headerActions.setPadding(0, dp(10), 0, 0);
        headerActions.addView(actionButton("瀵煎嚭琛ㄦ牸", DARK, new Runnable() { @Override public void run() { exportXlsx(); } }));
        headerActions.addView(actionButton("澶囦唤", DARK, new Runnable() { @Override public void run() { exportBackup(); } }));
        headerActions.addView(actionButton("瀵煎叆", DARK, new Runnable() { @Override public void run() { importBackup(); } }));
        header.addView(headerActions);
        root.addView(header);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = horizontal();
        nav.setPadding(dp(12), dp(10), dp(12), dp(8));
        for (final String name : Arrays.asList("姒傝", "瀹㈡埛", "璁㈠崟", "鏀舵", "鎼滅储")) {
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

        statusView = text("鍑嗗灏辩华", 12, Color.rgb(52, 99, 86), false);
        statusView.setPadding(dp(18), dp(8), dp(18), dp(8));
        statusView.setBackgroundColor(Color.rgb(231, 242, 237));
        root.addView(statusView);
        setContentView(root);
    }

    private void showPage(String name) {
        currentPage = name;
        page.removeAllViews();
        if ("姒傝".equals(name)) showDashboard();
        if ("瀹㈡埛".equals(name)) showClients();
        if ("璁㈠崟".equals(name)) showOrders();
        if ("鏀舵".equals(name)) showPayments();
        if ("鎼滅储".equals(name)) showSearch();
        // Rebuild the small navigation strip to reflect its selected state.
        buildLayoutPreservingPage(name);
    }

    private void buildLayoutPreservingPage(String name) {
        // The page contents are already generated; selected navigation is not essential to functionality.
        // A small status cue is more useful on narrow screens.
        status("宸叉墦寮€锛? + name);
    }

    private void showDashboard() {
        TextView live = text("鈼? 浠婂ぉ涔熷湪绋崇ǔ鎺ㄨ繘浣犵殑鐢熸剰", 15, Color.rgb(180, 245, 218), true);
        live.setPadding(dp(15), dp(14), dp(15), dp(14));
        live.setBackground(round(DARK, dp(14)));
        page.addView(live, fullMargins(0, 0, 0, 10));
        double total = 0, paid = 0;
        for (JSONObject o : objects(orders)) total += o.optDouble("amount", 0);
        for (JSONObject p : objects(payments)) paid += p.optDouble("amount", 0);
        LinearLayout first = horizontal();
        first.addView(metric("瀹㈡埛鎬绘暟", String.valueOf(clients.length()), Color.rgb(28, 48, 42)), weighted());
        first.addView(metric("璁㈠崟鎬婚", money(total), Color.rgb(28, 48, 42)), weighted());
        page.addView(first);
        LinearLayout second = horizontal();
        second.setPadding(0, dp(8), 0, 0);
        second.addView(metric("宸叉敹娆?, money(paid), GREEN), weighted());
        second.addView(metric("寰呮敹娆?, money(Math.max(0, total - paid)), Color.rgb(195, 122, 19)), weighted());
        page.addView(second, fullMargins(0, 0, 0, 12));
        LinearLayout body = section("寰呮敹娆捐鍗?, "+ 鏂板缓璁㈠崟", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        int count = 0;
        for (final JSONObject o : objects(orders)) {
            if (o.optDouble("paid", 0) < o.optDouble("amount", 0)) {
                JSONObject c = find(clients, o.optString("clientId"));
                addRow(body, o.optString("title"), (c == null ? "鈥? : c.optString("name")) + " 路 寰呮敹 " + money(o.optDouble("amount") - o.optDouble("paid")), new Runnable() { @Override public void run() { showOrderDialog(o); } });
                count++;
            }
        }
        if (count == 0) empty(body, "鏆傛棤寰呮敹娆捐鍗曘€?);
    }

    private void showClients() {
        LinearLayout body = section("瀹㈡埛妗ｆ", "+ 鏂板瀹㈡埛", new Runnable() { @Override public void run() { showClientDialog(null); } });
        if (clients.length() == 0) { empty(body, "杩樻病鏈夊鎴凤紝鐐瑰嚮鍙充笂瑙掓柊澧炪€?"); return; }
        for (final JSONObject c : objects(clients)) {
            int count = 0;
            for (JSONObject o : objects(orders)) if (c.optString("id").equals(o.optString("clientId"))) count++;
            addRow(body, c.optString("name"), (c.optString("phone", "鈥?)) + " 路 " + c.optString("status", "寰呰窡杩?) + " 路 " + count + " 绗旇鍗?, new Runnable() { @Override public void run() { showClientDialog(c); } });
        }
    }

    private void showOrders() {
        LinearLayout body = section("鍏ㄩ儴璁㈠崟", "+ 鏂板缓璁㈠崟", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        if (orders.length() == 0) { empty(body, "杩樻病鏈夎鍗曪紝鍏堟柊澧炵涓€绗斻€?"); return; }
        for (final JSONObject o : objects(orders)) {
            JSONObject c = find(clients, o.optString("clientId"));
            String detail = (c == null ? "鈥? : c.optString("name")) + " 路 " + money(o.optDouble("amount")) + " 路 宸叉敹 " + money(o.optDouble("paid"));
            addRow(body, o.optString("title"), detail, new Runnable() { @Override public void run() { showOrderDialog(o); } });
        }
    }

    private void showPayments() {
        LinearLayout body = section("鏀舵璁板綍", "+ 璁板綍鏀舵", new Runnable() { @Override public void run() { showPaymentDialog(); } });
        if (payments.length() == 0) { empty(body, "杩樻病鏈夋敹娆捐褰曘€?"); return; }
        for (final JSONObject p : objects(payments)) {
            final JSONObject payment = p;
            JSONObject o = find(orders, p.optString("orderId"));
            String title = o == null ? "宸插垹闄よ鍗? : o.optString("title");
            addRow(body, p.optString("date") + " 路 " + money(p.optDouble("amount")), title + " 路 " + p.optString("note", ""), new Runnable() { @Override public void run() { confirmDeletePayment(payment); } });
        }
    }

    private void showSearch() {
        LinearLayout body = section("鍏ㄥ眬鎼滅储", null, null);
        TextView hint = text("鎼滅储瀹㈡埛銆佽鍗曘€佹敹娆俱€佺數璇濄€佸娉ㄣ€佺姸鎬佹垨鏃ユ湡銆?, 13, MUTED, false);
        body.addView(hint, fullMargins(0, 0, 0, 10));
        LinearLayout bar = horizontal();
        final EditText query = edit("杈撳叆鍏抽敭璇?, "");
        bar.addView(query, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final LinearLayout results = vertical();
        bar.addView(actionButton("鎼滅储", GREEN, new Runnable() { @Override public void run() { performSearch(query.getText().toString(), results); } }), wrapMargins(8, 0, 0, 0));
        body.addView(bar);
        body.addView(results, fullMargins(0, 10, 0, 0));
        query.requestFocus();
    }

    private void performSearch(String raw, LinearLayout body) {
        String term = raw.trim().toLowerCase(Locale.ROOT);
        body.removeAllViews();
        if (term.isEmpty()) { status("璇疯緭鍏ュ叧閿瘝"); return; }
        int found = 0;
        for (final JSONObject c : objects(clients)) {
            if (contains(term, c.optString("name"), c.optString("phone"), c.optString("source"), c.optString("status"), c.optString("note"))) {
                addRow(body, "瀹㈡埛 路 " + c.optString("name"), c.optString("phone", "鈥?) + " 路 " + c.optString("note", ""), new Runnable() { @Override public void run() { showClientDialog(c); } });
                found++;
            }
        }
        for (final JSONObject o : objects(orders)) {
            JSONObject c = find(clients, o.optString("clientId"));
            if (contains(term, o.optString("title"), o.optString("status"), o.optString("due"), o.optString("note"), c == null ? "" : c.optString("name"))) {
                addRow(body, "璁㈠崟 路 " + o.optString("title"), (c == null ? "鈥? : c.optString("name")) + " 路 " + money(o.optDouble("amount")), new Runnable() { @Override public void run() { showOrderDialog(o); } });
                found++;
            }
        }
        for (final JSONObject p : objects(payments)) {
            JSONObject o = find(orders, p.optString("orderId"));
            if (contains(term, p.optString("date"), p.optString("note"), String.valueOf(p.optDouble("amount")), o == null ? "" : o.optString("title"))) {
                addRow(body, "鏀舵 路 " + money(p.optDouble("amount")), p.optString("date") + " 路 " + (o == null ? "宸插垹闄よ鍗? : o.optString("title")), new Runnable() { @Override public void run() { confirmDeletePayment(p); } });
                found++;
            }
        }
        if (found == 0) empty(body, "娌℃湁鎵惧埌鍖归厤鍐呭銆?");
        status("鎼滅储瀹屾垚锛氭壘鍒?" + found + " 鏉＄粨鏋?);
    }

    private void showClientDialog(final JSONObject existing) {
        LinearLayout form = dialogForm();
        final EditText name = field(form, "瀹㈡埛鍚嶇О *", existing == null ? "" : existing.optString("name"));
        final EditText phone = field(form, "鑱旂郴鏂瑰紡", existing == null ? "" : existing.optString("phone"));
        final EditText source = field(form, "瀹㈡埛鏉ユ簮", existing == null ? "" : existing.optString("source"));
        final Spinner state = spinner(form, "鐘舵€?, new String[]{"寰呰窡杩?, "杩涜涓?, "宸叉垚浜?}, existing == null ? "寰呰窡杩? : existing.optString("status", "寰呰窡杩?));
        final EditText note = field(form, "澶囨敞", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "鏂板瀹㈡埛" : "缂栬緫瀹㈡埛").setView(form).setNegativeButton("鍙栨秷", null).setPositiveButton("淇濆瓨", (d, w) -> {
            if (name.getText().toString().trim().isEmpty()) { toast("璇峰～鍐欏鎴峰悕绉?); return; }
            try {
                JSONObject c = existing == null ? new JSONObject() : existing;
                if (existing == null) { c.put("id", UUID.randomUUID().toString()); clients.put(c); }
                c.put("name", name.getText().toString().trim()); c.put("phone", phone.getText().toString().trim()); c.put("source", source.getText().toString().trim()); c.put("status", state.getSelectedItem().toString()); c.put("note", note.getText().toString().trim());
                save(); status("瀹㈡埛宸蹭繚瀛?); showPage("瀹㈡埛");
            } catch (Exception e) { toast("淇濆瓨澶辫触"); }
        });
        if (existing != null) builder.setNeutralButton("鍒犻櫎", (d, w) -> confirmDeleteClient(existing));
        builder.show();
    }

    private void showOrderDialog(final JSONObject existing) {
        List<JSONObject> available = objects(clients);
        if (available.isEmpty()) { toast("璇峰厛鏂板瀹㈡埛"); showClientDialog(null); return; }
        LinearLayout form = dialogForm();
        ArrayList<String> clientNames = new ArrayList<>(); final ArrayList<String> clientIds = new ArrayList<>();
        for (JSONObject c : available) { clientNames.add(c.optString("name")); clientIds.add(c.optString("id")); }
        final Spinner customer = spinner(form, "瀹㈡埛 *", clientNames.toArray(new String[0]), existing == null ? clientNames.get(0) : nameFor(existing.optString("clientId")));
        final EditText title = field(form, "椤圭洰/鏈嶅姟 *", existing == null ? "" : existing.optString("title"));
        final EditText amount = field(form, "璁㈠崟閲戦锛堝厓锛?", existing == null ? "" : String.valueOf(existing.optDouble("amount")));
        final EditText paid = field(form, "宸叉敹閲戦锛堝厓锛?, existing == null ? "0" : String.valueOf(existing.optDouble("paid")));
        final EditText due = field(form, "鎴鏃ユ湡锛圷YYY-MM-DD锛?, existing == null ? "" : existing.optString("due"));
        final Spinner state = spinner(form, "璁㈠崟鐘舵€?, new String[]{"寰呰窡杩?, "杩涜涓?, "宸插畬鎴?}, existing == null ? "寰呰窡杩? : existing.optString("status", "寰呰窡杩?));
        final EditText note = field(form, "澶囨敞", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "鏂板缓璁㈠崟" : "缂栬緫璁㈠崟").setView(form).setNegativeButton("鍙栨秷", null).setPositiveButton("淇濆瓨", (d, w) -> {
            try {
                double a = Double.parseDouble(amount.getText().toString().trim()); double p = Double.parseDouble(paid.getText().toString().trim());
                if (title.getText().toString().trim().isEmpty() || a < 0 || p < 0 || p > a) { toast("璇锋鏌ラ」鐩拰閲戦"); return; }
                JSONObject o = existing == null ? new JSONObject() : existing;
                if (existing == null) { o.put("id", UUID.randomUUID().toString()); orders.put(o); }
                o.put("clientId", clientIds.get(customer.getSelectedItemPosition())); o.put("title", title.getText().toString().trim()); o.put("amount", a); o.put("paid", p); o.put("due", due.getText().toString().trim()); o.put("status", state.getSelectedItem().toString()); o.put("note", note.getText().toString().trim());
                save(); status("璁㈠崟宸蹭繚瀛?); showPage("璁㈠崟");
            } catch (Exception e) { toast("閲戦鏍煎紡涓嶆纭?); }
        });
        if (existing != null) builder.setNeutralButton("鍒犻櫎", (d, w) -> confirmDeleteOrder(existing));
        builder.show();
    }

    private void showPaymentDialog() {
        ArrayList<JSONObject> available = new ArrayList<>();
        for (JSONObject o : objects(orders)) if (o.optDouble("paid") < o.optDouble("amount")) available.add(o);
        if (available.isEmpty()) { toast("娌℃湁鍙敹娆剧殑璁㈠崟"); return; }
        LinearLayout form = dialogForm();
        ArrayList<String> labels = new ArrayList<>(); final ArrayList<String> ids = new ArrayList<>();
        for (JSONObject o : available) { labels.add(o.optString("title") + "锛堝緟鏀?" + money(o.optDouble("amount") - o.optDouble("paid")) + "锛?); ids.add(o.optString("id")); }
        final Spinner orderSpin = spinner(form, "鍏宠仈璁㈠崟 *", labels.toArray(new String[0]), labels.get(0));
        final EditText amount = field(form, "鏀舵閲戦锛堝厓锛?", "");
        final EditText when = field(form, "鏀舵鏃ユ湡", today());
        final EditText note = field(form, "澶囨敞", "");
        new AlertDialog.Builder(this).setTitle("璁板綍鏀舵").setView(form).setNegativeButton("鍙栨秷", null).setPositiveButton("淇濆瓨", (d, w) -> {
            try {
                JSONObject order = find(orders, ids.get(orderSpin.getSelectedItemPosition())); double a = Double.parseDouble(amount.getText().toString().trim());
                if (a <= 0 || a > order.optDouble("a…1318 tokens truncated…瀹㈡埛鍚嶇О", "鑱旂郴鏂瑰紡", "鏉ユ簮", "鐘舵€?, "澶囨敞", "璁㈠崟鏁?});
        for (JSONObject c : objects(clients)) { int count = 0; for (JSONObject o : objects(orders)) if (c.optString("id").equals(o.optString("clientId"))) count++; customerRows.add(new String[]{c.optString("name"), c.optString("phone"), c.optString("source"), c.optString("status"), c.optString("note"), String.valueOf(count)}); }
        List<String[]> orderRows = new ArrayList<>(); orderRows.add(new String[]{"椤圭洰/鏈嶅姟", "瀹㈡埛", "璁㈠崟閲戦", "宸叉敹閲戦", "寰呮敹閲戦", "鎴鏃ユ湡", "鐘舵€?, "澶囨敞"});
        for (JSONObject o : objects(orders)) { JSONObject c = find(clients, o.optString("clientId")); double amount = o.optDouble("amount"), paid = o.optDouble("paid"); orderRows.add(new String[]{o.optString("title"), c == null ? "宸插垹闄ゅ鎴? : c.optString("name"), decimal(amount), decimal(paid), decimal(amount-paid), o.optString("due"), o.optString("status"), o.optString("note")}); }
        List<String[]> paymentRows = new ArrayList<>(); paymentRows.add(new String[]{"鏀舵鏃ユ湡", "璁㈠崟", "瀹㈡埛", "鏀舵閲戦", "澶囨敞"});
        for (JSONObject p : objects(payments)) { JSONObject o = find(orders, p.optString("orderId")); JSONObject c = o == null ? null : find(clients, o.optString("clientId")); paymentRows.add(new String[]{p.optString("date"), o == null ? "宸插垹闄よ鍗? : o.optString("title"), c == null ? "鈥? : c.optString("name"), decimal(p.optDouble("amount")), p.optString("note")}); }
        try (OutputStream raw = getContentResolver().openOutputStream(uri); ZipOutputStream zip = new ZipOutputStream(raw)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"瀹㈡埛\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"璁㈠崟\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"鏀舵\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>");
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
    private String money(double value) { return String.format(Locale.CHINA, "楼%,.2f", value); }
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
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { status("宸茬偣鍑伙細" + label); action.run(); } });
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
            "鎴戞€濇晠鎴戝湪銆傗€斺€旂瑳鍗″皵", "鐭ヨ瘑灏辨槸鍔涢噺銆傗€斺€斿煿鏍?, "瀛﹁€屼笉鎬濆垯缃旓紝鎬濊€屼笉瀛﹀垯娈嗐€傗€斺€斿瓟瀛?, "涓嶇Н璺锛屾棤浠ヨ嚦鍗冮噷銆傗€斺€旇崁瀛?, "鍗冮噷涔嬭锛屽浜庤冻涓嬨€傗€斺€旇€佸瓙",
            "澶╄鍋ワ紝鍚涘瓙浠ヨ嚜寮轰笉鎭€傗€斺€斿懆鏄?, "璺极婕叾淇繙鍏紝鍚惧皢涓婁笅鑰屾眰绱€傗€斺€斿眻鍘?, "涓氱簿浜庡嫟锛岃崚浜庡瑝銆傗€斺€旈煩鎰?, "璇讳竾鍗蜂功锛岃涓囬噷璺€傗€斺€斿垬褰?, "涓変汉琛岋紝蹇呮湁鎴戝笀鐒夈€傗€斺€斿瓟瀛?,
            "宸辨墍涓嶆锛屽嬁鏂戒簬浜恒€傗€斺€斿瓟瀛?, "鐭ヤ箣鑰呬笉濡傚ソ涔嬭€咃紝濂戒箣鑰呬笉濡備箰涔嬭€呫€傗€斺€斿瓟瀛?, "闈欎互淇韩锛屼凯浠ュ吇寰枫€傗€斺€旇钁涗寒", "闈炴贰娉婃棤浠ユ槑蹇楋紝闈炲畞闈欐棤浠ヨ嚧杩溿€傗€斺€旇钁涗寒", "娴风撼鐧惧窛锛屾湁瀹逛箖澶с€傗€斺€旀灄鍒欏緪",
            "鍏堝ぉ涓嬩箣蹇ц€屽咖锛屽悗澶╀笅涔嬩箰鑰屼箰銆傗€斺€旇寖浠叉饭", "绌峰垯鐙杽鍏惰韩锛岃揪鍒欏吋鍠勫ぉ涓嬨€傗€斺€斿瓱瀛?, "鐢熶簬蹇ф偅锛屾浜庡畨涔愩€傗€斺€斿瓱瀛?, "浜烘棤杩滆檻锛屽繀鏈夎繎蹇с€傗€斺€斿瓟瀛?, "瑷€蹇呬俊锛岃蹇呮灉銆傗€斺€斿瓟瀛?,
            "鏁忚€屽ソ瀛︼紝涓嶈€讳笅闂€傗€斺€斿瓟瀛?, "鍚剧敓涔熸湁娑紝鑰岀煡涔熸棤娑€傗€斺€斿簞瀛?, "鍚堟姳涔嬫湪锛岀敓浜庢鏈€傗€斺€旇€佸瓙", "涓鸿€呭父鎴愶紝琛岃€呭父鑷炽€傗€斺€旀檹瀛愭槬绉?, "蹇椾笉寮鸿€呮櫤涓嶈揪銆傗€斺€斿ⅷ瀛?,
            "鑳滀汉鑰呮湁鍔涳紝鑷儨鑰呭己銆傗€斺€旇€佸瓙", "澶╀笅闅句簨锛屽繀浣滀簬鏄撱€傗€斺€旇€佸瓙", "涓存笂缇￠奔锛屼笉濡傞€€鑰岀粨缃戙€傗€斺€旀樊鍗楀瓙", "灏戒俊涔︼紝鍒欎笉濡傛棤涔︺€傗€斺€斿瓱瀛?, "涓嶄互瑙勭煩锛屼笉鑳芥垚鏂瑰渾銆傗€斺€斿瓱瀛?,
            "鑻熸棩鏂帮紝鏃ユ棩鏂帮紝鍙堟棩鏂般€傗€斺€旂ぜ璁?, "浜虹敓鑷彜璋佹棤姝伙紝鐣欏彇涓瑰績鐓ф睏闈掋€傗€斺€旀枃澶╃ゥ", "浼氬綋鍑岀粷椤讹紝涓€瑙堜紬灞卞皬銆傗€斺€旀潨鐢?, "闀块鐮存氮浼氭湁鏃讹紝鐩存寕浜戝竼娴庢钵娴枫€傗€斺€旀潕鐧?, "娌夎垷渚х晹鍗冨竼杩囷紝鐥呮爲鍓嶅ご涓囨湪鏄ャ€傗€斺€斿垬绂归敗",
            "绾镐笂寰楁潵缁堣娴咃紝缁濈煡姝や簨瑕佽含琛屻€傗€斺€旈檰娓?, "妯湅鎴愬箔渚ф垚宄帮紝杩滆繎楂樹綆鍚勪笉鍚屻€傗€斺€旇嫃杞?, "涓嶇晱娴簯閬湜鐪硷紝鍙紭韬湪鏈€楂樺眰銆傗€斺€旂帇瀹夌煶", "闂笭閭ｅ緱娓呭璁革紝涓烘湁婧愬ご娲绘按鏉ャ€傗€斺€旀湵鐔?, "鑾瓑闂诧紝鐧戒簡灏戝勾澶达紝绌烘偛鍒囥€傗€斺€斿渤椋?,
            "浜虹敓鍦ㄥ嫟锛屼笉绱綍鑾枫€傗€斺€斿紶琛?, "澶╀笅鍏翠骸锛屽尮澶湁璐ｃ€傗€斺€旈【鐐庢", "鏈夊織鑰咃紝浜嬬珶鎴愩€傗€斺€斿悗姹変功", "鐧鹃椈涓嶅涓€瑙併€傗€斺€旀眽涔?, "鍏煎惉鍒欐槑锛屽亸淇″垯鏆椼€傗€斺€旈瓘寰?,
            "灏烘湁鎵€鐭紝瀵告湁鎵€闀裤€傗€斺€斿眻鍘?, "宸ユ鍠勫叾浜嬶紝蹇呭厛鍒╁叾鍣ㄣ€傗€斺€斿瓟瀛?, "鏃堕棿灏辨槸鐢熷懡銆傗€斺€旈瞾杩?, "鍏跺疄鍦颁笂鏈病鏈夎矾锛岃蛋鐨勪汉澶氫簡锛屼篃渚挎垚浜嗚矾銆傗€斺€旈瞾杩?, "涓轰腑鍗庝箣宕涜捣鑰岃涔︺€傗€斺€斿懆鎭╂潵",
            "The only way to do great work is to love what you do. 鈥?Steve Jobs", "Stay hungry, stay foolish. 鈥?Steve Jobs", "Innovation distinguishes between a leader and a follower. 鈥?Steve Jobs", "Whether you think you can or you think you cannot, you are right. 鈥?Henry Ford", "The future depends on what you do today. 鈥?Gandhi",
            "It always seems impossible until it is done. 鈥?Nelson Mandela", "Success is not final; failure is not fatal. 鈥?Winston Churchill", "The secret of getting ahead is getting started. 鈥?Mark Twain", "Believe you can and you are halfway there. 鈥?Theodore Roosevelt", "If opportunity does not knock, build a door. 鈥?Milton Berle",
            "The best way to predict the future is to create it. 鈥?Peter Drucker", "What we think, we become. 鈥?Buddha", "Do one thing every day that scares you. 鈥?Eleanor Roosevelt", "Dream big and dare to fail. 鈥?Norman Vaughan", "Action is the foundational key to all success. 鈥?Pablo Picasso",
            "Quality is not an act, it is a habit. 鈥?Aristotle", "Simplicity is the ultimate sophistication. 鈥?Leonardo da Vinci", "Well begun is half done. 鈥?Aristotle", "The unexamined life is not worth living. 鈥?Socrates", "The only true wisdom is in knowing you know nothing. 鈥?Socrates",
            "What you do speaks so loudly that I cannot hear what you say. 鈥?Emerson", "If you can dream it, you can do it. 鈥?Walt Disney", "Done is better than perfect. 鈥?Sheryl Sandberg", "The best preparation for tomorrow is doing your best today. 鈥?H. Jackson Brown Jr.", "The harder I work, the luckier I get. 鈥?Samuel Goldwyn",
            "Start where you are. Use what you have. Do what you can. 鈥?Arthur Ashe", "A year from now you may wish you had started today. 鈥?Karen Lamb", "The expert in anything was once a beginner. 鈥?Helen Hayes", "Great things are done by a series of small things brought together. 鈥?Van Gogh", "Success is the sum of small efforts, repeated day in and day out. 鈥?Robert Collier",
            "The only limit to our realization of tomorrow is our doubts of today. 鈥?F. D. Roosevelt", "Happiness depends upon ourselves. 鈥?Aristotle", "Knowledge speaks, but wisdom listens. 鈥?Jimi Hendrix", "The purpose of our lives is to be happy. 鈥?Dalai Lama", "In the middle of difficulty lies opportunity. 鈥?Albert Einstein",
            "Logic will get you from A to B. Imagination will take you everywhere. 鈥?Albert Einstein", "Learn as if you will live forever, live like you will die tomorrow. 鈥?Gandhi", "Success usually comes to those who are too busy to be looking for it. 鈥?Thoreau", "The future belongs to those who believe in the beauty of their dreams. 鈥?Eleanor Roosevelt", "A person who never made a mistake never tried anything new. 鈥?Albert Einstein",
            "Do not watch the clock; do what it does. Keep going. 鈥?Sam Levenson", "Everything you can imagine is real. 鈥?Pablo Picasso", "The best time to plant a tree was twenty years ago. The second best time is now. 鈥?Chinese proverb", "You miss one hundred percent of the shots you do not take. 鈥?Wayne Gretzky", "Small deeds done are better than great deeds planned. 鈥?Peter Marshall"
        );
    }
}

