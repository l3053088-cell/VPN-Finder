package com.example.vpnfinder;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String[] SOURCES = {
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/Splitted-By-Protocol/trojan.txt",
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/Splitted-By-Protocol/ss.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Splitted-By-Protocol/vmess.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Splitted-By-Protocol/trojan.txt",
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/all_configs.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge_base64.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/server.txt",
        "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/sub.txt",
        "https://raw.githubusercontent.com/Surfboardv2ray/TGParse/main/splitted/mixed",
        "https://raw.githubusercontent.com/mheidari98/.proxy/main/all"
    };
    private static final Pattern LINK = Pattern.compile("(?i)(?:vless|vmess|trojan|ss|ssr|hysteria2?|hy2|tuic)://[^\\s\"'<>\\\\`]+");
    private final Set<String> links = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<String> working = Collections.synchronizedList(new ArrayList<>());
    private TextView status, output;
    private Button scanButton, verifyButton, exportButton;
    private volatile boolean cancelled = false;
    private ExecutorService worker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 20);
        root.setBackgroundColor(Color.rgb(16, 24, 38));

        TextView title = new TextView(this);
        title.setText("VPN-Finder");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setText("Поиск публичных VPN-конфигураций\nБыстрый поиск проверяет TCP-доступность сервера.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(16);
        status.setPadding(0, 18, 0, 18);
        root.addView(status);

        scanButton = button("НАЙТИ VPN-ССЫЛКИ");
        scanButton.setOnClickListener(v -> startScan());
        root.addView(scanButton);

        verifyButton = button("ПРОВЕРИТЬ TCP-ДОСТУПНОСТЬ");
        verifyButton.setEnabled(false);
        verifyButton.setOnClickListener(v -> startVerify());
        root.addView(verifyButton);

        exportButton = button("СОХРАНИТЬ РЕЗУЛЬТАТЫ");
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(v -> exportResults());
        root.addView(exportButton);

        Button stop = button("ОСТАНОВИТЬ");
        stop.setOnClickListener(v -> { cancelled = true; status.setText("Остановка…"); });
        root.addView(stop);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(Color.rgb(205, 220, 235));
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setPadding(4, 16, 4, 16);
        scroll.addView(output);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.topMargin = 12;
        root.addView(scroll, lp);
        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private void startScan() {
        if (worker != null && !worker.isTerminated()) { status.setText("Операция уже выполняется."); return; }
        cancelled = false;
        links.clear(); working.clear();
        output.setText("");
        scanButton.setEnabled(false);
        verifyButton.setEnabled(false);
        exportButton.setEnabled(false);
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            int done = 0;
            for (String source : SOURCES) {
                if (cancelled) break;
                try {
                    String body = download(source);
                    extract(body);
                    String decoded = decodeSubscription(body);
                    if (!decoded.equals(body)) extract(decoded);
                } catch (Exception ignored) { }
                done++;
                final int d = done, count = links.size();
                runOnUiThread(() -> status.setText("Источники: " + d + "/" + SOURCES.length + " · Найдено уникальных ссылок: " + count));
            }
            runOnUiThread(() -> {
                scanButton.setEnabled(true);
                verifyButton.setEnabled(!links.isEmpty());
                exportButton.setEnabled(!links.isEmpty());
                status.setText((cancelled ? "Поиск остановлен. " : "Поиск завершён. ") + "Найдено ссылок: " + links.size());
                output.setText("Найдено " + links.size() + " уникальных конфигураций.\nНажми «Проверить TCP-доступность» для проверки серверов.\n\n" + preview(links, 40));
            });
            worker.shutdown();
        });
    }

    private String download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "VPN-Finder-Android/1.0");
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0 && out.size() < 8 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private void extract(String text) {
        Matcher m = LINK.matcher(text);
        while (m.find()) {
            String s = m.group().replaceAll("[,;\\])}]+$", "");
            if (s.length() > 15 && s.length() < 12000) links.add(s);
        }
    }

    private String decodeSubscription(String text) {
        String t = text.trim().replaceAll("\\s+", "");
        if (t.length() < 40 || !t.matches("[A-Za-z0-9_+/=-]+")) return text;
        try {
            String padded = t + "====".substring(0, (4 - t.length() % 4) % 4);
            return new String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) { return text; }
    }

    private void startVerify() {
        if (links.isEmpty()) return;
        cancelled = false;
        verifyButton.setEnabled(false);
        scanButton.setEnabled(false);
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            List<String> snapshot;
            synchronized (links) { snapshot = new ArrayList<>(links); }
            ExecutorService pool = Executors.newFixedThreadPool(20);
            List<Future<String>> futures = new ArrayList<>();
            for (String link : snapshot) {
                if (cancelled) break;
                futures.add(pool.submit(() -> checkTcp(link)));
            }
            int finished = 0;
            for (Future<String> f : futures) {
                if (cancelled) break;
                try {
                    String result = f.get(4, TimeUnit.SECONDS);
                    if (result != null) working.add(result);
                } catch (Exception ignored) { }
                finished++;
                if (finished % 20 == 0) {
                    final int n = finished, total = futures.size(), ok = working.size();
                    runOnUiThread(() -> status.setText("TCP-проверка: " + n + "/" + total + " · Доступно: " + ok));
                }
            }
            pool.shutdownNow();
            runOnUiThread(() -> {
                scanButton.setEnabled(true);
                verifyButton.setEnabled(!links.isEmpty());
                exportButton.setEnabled(!working.isEmpty() || !links.isEmpty());
                status.setText("TCP-проверка завершена. Доступно серверов: " + working.size() + " из " + links.size());
                output.setText("TCP-доступные конфигурации: " + working.size() + "\nВажно: TCP-проверка не подтверждает работу VPN-протокола.\n\n" + preview(working, 80));
            });
            worker.shutdown();
        });
    }

    private String checkTcp(String link) {
        try {
            String scheme = link.substring(0, link.indexOf("://")).toLowerCase(Locale.ROOT);
            String host = null; int port = -1;
            if (scheme.equals("vmess")) {
                String raw = link.substring(8).split("[?#]", 2)[0];
                String json = new String(android.util.Base64.decode(pad(raw), android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
                JSONObject o = new JSONObject(json);
                host = o.optString("add", null); port = Integer.parseInt(o.optString("port", "-1"));
            } else {
                URI u = URI.create(link);
                host = u.getHost(); port = u.getPort();
                if (host == null && scheme.equals("ss")) {
                    String auth = u.getRawSchemeSpecificPart().split("@", 2)[0];
                    String dec = new String(android.util.Base64.decode(pad(auth), android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
                    String[] hp = dec.substring(dec.lastIndexOf(':') + 1).split(":", 2);
                    if (hp.length == 2) port = Integer.parseInt(hp[1]);
                    host = dec.substring(dec.lastIndexOf(':') + 1, dec.lastIndexOf(':') + 1 + (hp.length == 2 ? hp[0].length() : 0));
                }
            }
            if (host == null || port < 1 || port > 65535) return null;
            long start = System.nanoTime();
            try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(host, port), 1800); }
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return link;
        } catch (Exception e) { return null; }
    }

    private String pad(String s) { return s + "====".substring(0, (4 - s.length() % 4) % 4); }

    private String preview(Collection<String> items, int max) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String s : items) { if (n++ >= max) break; b.append(s).append("\n"); }
        if (items.size() > max) b.append("\n… ещё ").append(items.size() - max).append(" записей");
        return b.toString();
    }

    private String protocolFileName(String link) {
        int separator = link.indexOf("://");
        if (separator <= 0) return null;
        String scheme = link.substring(0, separator).toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "vless": return "vless_links.txt";
            case "vmess": return "vmess_links.txt";
            case "trojan": return "trojan_links.txt";
            case "ss": return "ss_links.txt";
            case "ssr": return "ssr_links.txt";
            case "hysteria": return "hysteria_links.txt";
            case "hysteria2": return "hysteria2_links.txt";
            case "hy2": return "hy2_links.txt";
            case "tuic": return "tuic_links.txt";
            default: return null;
        }
    }

    private Uri findExistingDownload(String displayName) {
        if (Build.VERSION.SDK_INT < 29) return null;
        String[] projection = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " +
                MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] args = { displayName, "Download/VPN-Finder/" };
        try (android.database.Cursor cursor = getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void saveProtocolFile(String displayName, List<String> entries) throws IOException {
        if (Build.VERSION.SDK_INT < 29)
            throw new IOException("Для сохранения в общую папку Downloads требуется Android 10 или новее.");
        Uri uri = findExistingDownload(displayName);
        if (uri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/VPN-Finder");
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        if (uri == null) throw new IOException("Не удалось создать файл " + displayName);
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Не удалось открыть файл " + displayName);
            for (String entry : entries) {
                out.write(entry.getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
        }
    }

    private void exportResults() {
        final List<String> rows;
        synchronized (working) {
            rows = new ArrayList<>(working.isEmpty() ? links : working);
        }
        if (rows.isEmpty()) {
            status.setText("Нет ссылок для сохранения.");
            return;
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String link : rows) {
            String fileName = protocolFileName(link);
            if (fileName == null) continue;
            if (!grouped.containsKey(fileName)) grouped.put(fileName, new ArrayList<>());
            grouped.get(fileName).add(link);
        }

        int savedFiles = 0;
        int savedLinks = 0;
        StringBuilder report = new StringBuilder("Сохранение по протоколам:\n");
        try {
            for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                saveProtocolFile(entry.getKey(), entry.getValue());
                savedFiles++;
                savedLinks += entry.getValue().size();
                report.append(entry.getKey()).append(" — ")
                        .append(entry.getValue().size()).append(" ссылок\n");
            }
            if (savedFiles == 0) {
                status.setText("Не удалось определить типы ссылок для экспорта.");
                return;
            }
            report.append("\nПапка: Download/VPN-Finder");
            report.append("\nВсего: ").append(savedLinks).append(" ссылок в ")
                    .append(savedFiles).append(" файлах.");
            status.setText("Сохранено: " + savedLinks + " ссылок в " + savedFiles + " отдельных файлах.");
            output.setText(report.toString());
        } catch (Exception e) {
            status.setText("Ошибка сохранения: " + e.getMessage());
        }
    }

    @Override protected void onDestroy() {
        cancelled = true;
        if (worker != null) worker.shutdownNow();
        super.onDestroy();
    }
}
