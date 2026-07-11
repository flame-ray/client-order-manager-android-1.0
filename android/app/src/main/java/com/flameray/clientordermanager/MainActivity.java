package com.flameray.clientordermanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int DARK = Color.rgb(20, 60, 52);
    private static final int GREEN = Color.rgb(8, 120, 94);
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
    private final Handler handler = new Handler();
    private final ArrayList<String> quotes = new ArrayList<>();
    private int quoteIndex = 0;

    private final Runnable quoteLoop = new Runnable() {
        @Override public void run() {
            if (!quotes.isEmpty()) {
                quoteView.setText(quotes.get(quoteIndex));
                quoteIndex = (quoteIndex + 1) % quotes.size();
                if (quoteIndex == 0) Collections.shuffle(quotes);
            }
            handler.postDelayed(this, 6000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("client_order_manager", MODE_PRIVATE);
        clients = load("clients");
        orders = load("orders");
        payments = load("payments");
        setupQuotes();
        Collections.shuffle(quotes);
        buildUi();
        showDashboard();
        handler.post(quoteLoop);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(quoteLoop);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout header = column();
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(DARK);
        header.addView(label("Client Order Manager 1.0", 22, Color.WHITE, true));
        quoteView = label("", 12, Color.rgb(190, 222, 210), false);
        quoteView.setPadding(0, dp(5), 0, 0);
        quoteView.setSingleLine(true);
        header.addView(quoteView);
        root.addView(header);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row();
        nav.setPadding(dp(12), dp(10), dp(12), dp(8));
        nav.addView(navButton("Dashboard", new Runnable() { @Override public void run() { showDashboard(); } }));
        nav.addView(navButton("Clients", new Runnable() { @Override public void run() { showClients(); } }));
        nav.addView(navButton("Orders", new Runnable() { @Override public void run() { showOrders(); } }));
        nav.addView(navButton("Payments", new Runnable() { @Override public void run() { showPayments(); } }));
        nav.addView(navButton("Search", new Runnable() { @Override public void run() { showSearch(); } }));
        navScroll.addView(nav);
        root.addView(navScroll);

        ScrollView scroll = new ScrollView(this);
        page = column();
        page.setPadding(dp(14), dp(4), dp(14), dp(18));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        statusView = label("Ready", 12, Color.rgb(52, 99, 86), false);
        statusView.setPadding(dp(18), dp(8), dp(18), dp(8));
        statusView.setBackgroundColor(Color.rgb(231, 242, 237));
        root.addView(statusView);
        setContentView(root);
    }

    private void showDashboard() {
        clearPage("Dashboard");
        TextView banner = label("OFFLINE MODE  -  Your data stays on this device", 14, Color.rgb(180, 245, 218), true);
        banner.setPadding(dp(14), dp(13), dp(14), dp(13));
        banner.setBackground(round(DARK, dp(14)));
        page.addView(banner, full(0, 0, 0, 10));
        double total = 0, received = 0;
        for (JSONObject order : objects(orders)) total += order.optDouble("amount");
        for (JSONObject payment : objects(payments)) received += payment.optDouble("amount");
        LinearLayout metricsA = row();
        metricsA.addView(metric("Clients", String.valueOf(clients.length()), DARK), weighted());
        metricsA.addView(metric("Orders", money(total), DARK), weighted());
        page.addView(metricsA);
        LinearLayout metricsB = row();
        metricsB.setPadding(0, dp(8), 0, dp(12));
        metricsB.addView(metric("Received", money(received), GREEN), weighted());
        metricsB.addView(metric("Outstanding", money(Math.max(0, total - received)), Color.rgb(195, 122, 19)), weighted());
        page.addView(metricsB);
        LinearLayout body = section("Outstanding Orders", "Add order", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        int count = 0;
        for (final JSONObject order : objects(orders)) {
            if (order.optDouble("paid") < order.optDouble("amount")) {
                JSONObject client = find(clients, order.optString("clientId"));
                addRow(body, order.optString("title"), name(client) + "  |  due " + money(order.optDouble("amount") - order.optDouble("paid")), new Runnable() { @Override public void run() { showOrderDialog(order); } });
                count++;
            }
        }
        if (count == 0) empty(body, "No outstanding orders.");
    }

    private void showClients() {
        clearPage("Clients");
        LinearLayout body = section("Clients", "Add client", new Runnable() { @Override public void run() { showClientDialog(null); } });
        if (clients.length() == 0) { empty(body, "No clients yet."); return; }
        for (final JSONObject client : objects(clients)) {
            int orderCount = 0;
            for (JSONObject order : objects(orders)) if (client.optString("id").equals(order.optString("clientId"))) orderCount++;
            addRow(body, client.optString("name"), client.optString("phone", "No phone") + "  |  " + orderCount + " orders", new Runnable() { @Override public void run() { showClientDialog(client); } });
        }
    }

    private void showOrders() {
        clearPage("Orders");
        LinearLayout body = section("Orders", "Add order", new Runnable() { @Override public void run() { showOrderDialog(null); } });
        if (orders.length() == 0) { empty(body, "No orders yet."); return; }
        for (final JSONObject order : objects(orders)) {
            JSONObject client = find(clients, order.optString("clientId"));
            addRow(body, order.optString("title"), name(client) + "  |  " + money(order.optDouble("amount")) + "  |  paid " + money(order.optDouble("paid")), new Runnable() { @Override public void run() { showOrderDialog(order); } });
        }
    }

    private void showPayments() {
        clearPage("Payments");
        LinearLayout body = section("Payments", "Record payment", new Runnable() { @Override public void run() { showPaymentDialog(); } });
        if (payments.length() == 0) { empty(body, "No payment records yet."); return; }
        for (final JSONObject payment : objects(payments)) {
            JSONObject order = find(orders, payment.optString("orderId"));
            addRow(body, money(payment.optDouble("amount")), (order == null ? "Deleted order" : order.optString("title")) + "  |  " + payment.optString("note", ""), new Runnable() { @Override public void run() { deletePayment(payment); } });
        }
    }

    private void showSearch() {
        clearPage("Search");
        LinearLayout body = section("Search all data", null, null);
        final EditText query = input("Search client, order, note or phone", "");
        body.addView(query, full(0, 0, 0, 8));
        final LinearLayout results = column();
        body.addView(actionButton("Search", GREEN, new Runnable() { @Override public void run() { search(query.getText().toString(), results); } }), wrap(0, 0, 0, 8));
        body.addView(results);
    }

    private void search(String text, LinearLayout results) {
        results.removeAllViews();
        String term = text.trim().toLowerCase(Locale.ROOT);
        if (term.length() == 0) { empty(results, "Type a search term."); return; }
        int matches = 0;
        for (final JSONObject client : objects(clients)) {
            if (has(term, client.optString("name"), client.optString("phone"), client.optString("note"))) {
                addRow(results, "Client: " + client.optString("name"), client.optString("phone", ""), new Runnable() { @Override public void run() { showClientDialog(client); } });
                matches++;
            }
        }
        for (final JSONObject order : objects(orders)) {
            if (has(term, order.optString("title"), order.optString("note"), order.optString("status"))) {
                addRow(results, "Order: " + order.optString("title"), money(order.optDouble("amount")), new Runnable() { @Override public void run() { showOrderDialog(order); } });
                matches++;
            }
        }
        for (JSONObject payment : objects(payments)) {
            JSONObject order = find(orders, payment.optString("orderId"));
            if (has(term, payment.optString("note"), String.valueOf(payment.optDouble("amount")), order == null ? "" : order.optString("title"))) {
                addRow(results, "Payment: " + money(payment.optDouble("amount")), order == null ? "Deleted order" : order.optString("title"), null);
                matches++;
            }
        }
        if (matches == 0) empty(results, "No matching records.");
        status("Search found " + matches + " results");
    }

    private void showClientDialog(final JSONObject existing) {
        LinearLayout form = form();
        final EditText clientName = field(form, "Client name", existing == null ? "" : existing.optString("name"));
        final EditText phone = field(form, "Phone or contact", existing == null ? "" : existing.optString("phone"));
        final EditText note = field(form, "Notes", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "Add client" : "Edit client").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null);
        if (existing != null) dialog.setNeutralButton("Delete", null);
        final AlertDialog box = dialog.create();
        box.setOnShowListener(listener -> {
            box.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (clientName.getText().toString().trim().isEmpty()) { toast("Client name is required"); return; }
                try {
                    JSONObject client = existing == null ? new JSONObject() : existing;
                    if (existing == null) { client.put("id", UUID.randomUUID().toString()); clients.put(client); }
                    client.put("name", clientName.getText().toString().trim()); client.put("phone", phone.getText().toString().trim()); client.put("note", note.getText().toString().trim());
                    save(); box.dismiss(); showClients();
                } catch (Exception e) { toast("Could not save client"); }
            });
            if (existing != null) box.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> deleteClient(existing, box));
        });
        box.show();
    }

    private void showOrderDialog(final JSONObject existing) {
        List<JSONObject> available = objects(clients);
        if (available.isEmpty()) { toast("Add a client first"); showClientDialog(null); return; }
        LinearLayout form = form();
        final ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < available.size(); i++) { JSONObject c = available.get(i); ids.add(c.optString("id")); names.add(c.optString("name")); if (existing != null && c.optString("id").equals(existing.optString("clientId"))) selected = i; }
        TextView customerLabel = label("Client", 12, MUTED, true); form.addView(customerLabel);
        final Spinner clientSpin = new Spinner(this); clientSpin.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names)); clientSpin.setSelection(selected); form.addView(clientSpin, full(0, 0, 0, 6));
        final EditText title = field(form, "Order title", existing == null ? "" : existing.optString("title"));
        final EditText amount = field(form, "Amount", existing == null ? "" : String.valueOf(existing.optDouble("amount")));
        final EditText paid = field(form, "Already paid", existing == null ? "0" : String.valueOf(existing.optDouble("paid")));
        final EditText note = field(form, "Notes", existing == null ? "" : existing.optString("note"));
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "Add order" : "Edit order").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null);
        if (existing != null) dialog.setNeutralButton("Delete", null);
        final AlertDialog box = dialog.create();
        box.setOnShowListener(listener -> {
            box.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    double total = Double.parseDouble(amount.getText().toString().trim()); double received = Double.parseDouble(paid.getText().toString().trim());
                    if (title.getText().toString().trim().isEmpty() || total < 0 || received < 0 || received > total) { toast("Check title and amounts"); return; }
                    JSONObject order = existing == null ? new JSONObject() : existing;
                    if (existing == null) { order.put("id", UUID.randomUUID().toString()); orders.put(order); }
                    order.put("clientId", ids.get(clientSpin.getSelectedItemPosition())); order.put("title", title.getText().toString().trim()); order.put("amount", total); order.put("paid", received); order.put("note", note.getText().toString().trim()); order.put("status", "Active");
                    save(); box.dismiss(); showOrders();
                } catch (Exception e) { toast("Amount must be a number"); }
            });
            if (existing != null) box.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> deleteOrder(existing, box));
        });
        box.show();
    }

    private void showPaymentDialog() {
        ArrayList<JSONObject> open = new ArrayList<>();
        for (JSONObject order : objects(orders)) if (order.optDouble("paid") < order.optDouble("amount")) open.add(order);
        if (open.isEmpty()) { toast("No unpaid orders"); return; }
        LinearLayout form = form();
        ArrayList<String> captions = new ArrayList<>(); final ArrayList<String> ids = new ArrayList<>();
        for (JSONObject order : open) { captions.add(order.optString("title") + " - due " + money(order.optDouble("amount") - order.optDouble("paid"))); ids.add(order.optString("id")); }
        final Spinner spin = new Spinner(this); spin.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, captions)); form.addView(spin, full(0, 0, 0, 6));
        final EditText amount = field(form, "Payment amount", "");
        final EditText note = field(form, "Note", "");
        final AlertDialog box = new AlertDialog.Builder(this).setTitle("Record payment").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        box.setOnShowListener(listener -> box.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                JSONObject order = find(orders, ids.get(spin.getSelectedItemPosition())); double value = Double.parseDouble(amount.getText().toString().trim());
                if (value <= 0 || value > order.optDouble("amount") - order.optDouble("paid")) { toast("Payment exceeds outstanding amount"); return; }
                JSONObject payment = new JSONObject(); payment.put("id", UUID.randomUUID().toString()); payment.put("orderId", order.optString("id")); payment.put("amount", value); payment.put("note", note.getText().toString().trim()); payments.put(payment); order.put("paid", order.optDouble("paid") + value);
                save(); box.dismiss(); showPayments();
            } catch (Exception e) { toast("Payment amount must be a number"); }
        }));
        box.show();
    }

    private void deleteClient(final JSONObject client, final AlertDialog box) {
        for (JSONObject order : objects(orders)) if (client.optString("id").equals(order.optString("clientId"))) { toast("Delete this client's orders first"); return; }
        clients = remove(clients, client.optString("id")); save(); box.dismiss(); showClients();
    }
    private void deleteOrder(final JSONObject order, final AlertDialog box) {
        orders = remove(orders, order.optString("id")); JSONArray next = new JSONArray(); for (JSONObject payment : objects(payments)) if (!order.optString("id").equals(payment.optString("orderId"))) next.put(payment); payments = next; save(); box.dismiss(); showOrders();
    }
    private void deletePayment(final JSONObject payment) {
        JSONObject order = find(orders, payment.optString("orderId"));
        try { if (order != null) order.put("paid", Math.max(0, order.optDouble("paid") - payment.optDouble("amount"))); } catch (Exception ignored) { }
        payments = remove(payments, payment.optString("id")); save(); showPayments();
    }

    private void clearPage(String name) { page.removeAllViews(); status("Opened " + name); }
    private void save() { prefs.edit().putString("clients", clients.toString()).putString("orders", orders.toString()).putString("payments", payments.toString()).apply(); }
    private JSONArray load(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private JSONObject find(JSONArray array, String id) { for (JSONObject item : objects(array)) if (id.equals(item.optString("id"))) return item; return null; }
    private JSONArray remove(JSONArray array, String id) { JSONArray next = new JSONArray(); for (JSONObject item : objects(array)) if (!id.equals(item.optString("id"))) next.put(item); return next; }
    private List<JSONObject> objects(JSONArray array) { ArrayList<JSONObject> out = new ArrayList<>(); for (int i = 0; i < array.length(); i++) { JSONObject item = array.optJSONObject(i); if (item != null) out.add(item); } return out; }
    private boolean has(String term, String... values) { for (String value : values) if ((value == null ? "" : value).toLowerCase(Locale.ROOT).contains(term)) return true; return false; }
    private String name(JSONObject client) { return client == null ? "Unknown client" : client.optString("name"); }
    private String money(double value) { return String.format(Locale.US, "$%,.2f", value); }
    private void status(String message) { statusView.setText(message); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); return box; }
    private TextView label(String value, int size, int color, boolean bold) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return text; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable round(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape; }
    private LinearLayout.LayoutParams full(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private LinearLayout.LayoutParams wrap(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private LinearLayout.LayoutParams weighted() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); p.setMargins(0, 0, dp(6), 0); return p; }
    private Button navButton(String text, final Runnable action) { Button button = actionButton(text, Color.rgb(225, 235, 230), action); button.setTextColor(GREEN); return button; }
    private Button actionButton(final String text, int background, final Runnable action) {
        final Button button = new Button(this); button.setAllCaps(false); button.setText(text); button.setTextSize(13); button.setTextColor(Color.WHITE); button.setPadding(dp(12), dp(4), dp(12), dp(4)); button.setBackground(round(background, dp(11)));
        button.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View view, MotionEvent event) { if (event.getAction() == MotionEvent.ACTION_DOWN) view.setAlpha(0.55f); if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) view.setAlpha(1f); return false; } });
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { status("Clicked " + text); action.run(); } });
        return button;
    }
    private LinearLayout metric(String caption, String value, int color) { LinearLayout card = column(); card.setPadding(dp(12), dp(10), dp(12), dp(10)); card.setBackground(round(PAPER, dp(12))); card.addView(label(caption, 12, MUTED, false)); card.addView(label(value, 17, color, true)); return card; }
    private LinearLayout section(String title, String button, Runnable action) { LinearLayout card = column(); card.setPadding(dp(13), dp(12), dp(13), dp(12)); card.setBackground(round(PAPER, dp(14))); LinearLayout header = row(); header.addView(label(title, 17, DARK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); if (button != null) header.addView(actionButton(button, GREEN, action)); card.addView(header, full(0, 0, 0, 8)); page.addView(card, full(0, 0, 0, 12)); return card; }
    private void addRow(LinearLayout target, String title, String detail, final Runnable action) { TextView row = label(title + "\n" + detail, 14, DARK, false); row.setPadding(dp(11), dp(10), dp(11), dp(10)); row.setBackground(round(Color.rgb(246, 250, 248), dp(10))); if (action != null) row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { view.setAlpha(0.6f); view.postDelayed(new Runnable() { @Override public void run() { view.setAlpha(1f); } }, 120); action.run(); } }); target.addView(row, full(0, 0, 0, 7)); }
    private void empty(LinearLayout target, String message) { TextView text = label(message, 14, MUTED, false); text.setGravity(Gravity.CENTER); text.setPadding(0, dp(24), 0, dp(24)); target.addView(text); }
    private LinearLayout form() { LinearLayout form = column(); form.setPadding(dp(8), dp(4), dp(8), dp(4)); return form; }
    private EditText input(String hint, String value) { EditText field = new EditText(this); field.setHint(hint); field.setText(value); field.setTextSize(15); field.setSingleLine(true); return field; }
    private EditText field(LinearLayout form, String title, String value) { form.addView(label(title, 12, MUTED, true), full(0, 6, 0, 0)); EditText field = input(title, value); form.addView(field, full(0, 0, 0, 4)); return field; }

    private void setupQuotes() {
        String[] base = new String[]{
            "The only way to do great work is to love what you do. - Steve Jobs", "Stay hungry, stay foolish. - Steve Jobs", "Knowledge is power. - Francis Bacon", "The journey of a thousand miles begins with one step. - Lao Tzu", "Action is the foundational key to success. - Pablo Picasso",
            "Quality is not an act, it is a habit. - Aristotle", "The secret of getting ahead is getting started. - Mark Twain", "It always seems impossible until it is done. - Nelson Mandela", "Dream big and dare to fail. - Norman Vaughan", "Well begun is half done. - Aristotle",
            "The best way to predict the future is to create it. - Peter Drucker", "Believe you can and you are halfway there. - Theodore Roosevelt", "Simplicity is the ultimate sophistication. - Leonardo da Vinci", "Success is the sum of small efforts. - Robert Collier", "Start where you are. Use what you have. Do what you can. - Arthur Ashe",
            "The harder I work, the luckier I get. - Samuel Goldwyn", "A year from now you may wish you had started today. - Karen Lamb", "The expert in anything was once a beginner. - Helen Hayes", "In the middle of difficulty lies opportunity. - Albert Einstein", "Everything you can imagine is real. - Pablo Picasso",
            "Do not watch the clock; do what it does. Keep going. - Sam Levenson", "Small deeds done are better than great deeds planned. - Peter Marshall", "The future depends on what you do today. - Gandhi", "What we think, we become. - Buddha", "Done is better than perfect. - Sheryl Sandberg"
        };
        for (int round = 0; round < 4; round++) for (String quote : base) quotes.add(quote);
    }
}

