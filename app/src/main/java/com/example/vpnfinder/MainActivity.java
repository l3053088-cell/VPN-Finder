package com.example.vpnfinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_TERMUX = 41;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showScreen();
        checkAndLaunch();
    }

    private void showScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.rgb(18, 25, 38));

        TextView title = new TextView(this);
        title.setText("VPN-Finder");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        status = new TextView(this);
        status.setText("Запуск поиска конфигураций через Termux");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 20, 0, 28);

        Button run = new Button(this);
        run.setText("ЗАПУСТИТЬ ПОИСК");
        run.setOnClickListener(v -> checkAndLaunch());

        root.addView(title);
        root.addView(status);
        root.addView(run);
        setContentView(root);
    }

    private void checkAndLaunch() {
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{"com.termux.permission.RUN_COMMAND"},
                    REQUEST_TERMUX
            );
        } else {
            launchFinder();
        }
    }

    private void launchFinder() {
        if (getPackageManager().getLaunchIntentForPackage("com.termux") == null) {
            status.setText("Termux не найден. Установи и открой Termux.");
            return;
        }

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "com.termux",
                "com.termux.app.RunCommandService"
        ));
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{"-lc", "python \"$HOME/vpn-finder/vpn_finder.py\""});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", 0);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "VPN-Finder");
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION",
                "Запуск поиска VPN-конфигураций");

        try {
            startService(intent);
            status.setText("Команда отправлена в Termux. Результаты будут в VPN-Results.");
            Toast.makeText(this, "VPN-Finder запущен", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            status.setText("Ошибка запуска Termux: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_TERMUX) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchFinder();
            } else {
                status.setText("Необходимо разрешить запуск команд в Termux.");
            }
        }
    }
}
