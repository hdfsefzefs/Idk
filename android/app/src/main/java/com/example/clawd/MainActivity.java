package com.example.clawd;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Clawd 桌寵");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        TextView hint = new TextView(this);
        hint.setText("讓 Clawd 在你的手機畫面上走來走去。\n需要「顯示在其他應用程式上層」的權限。");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, pad / 2, 0, pad);

        String saved = getSharedPreferences("pet", MODE_PRIVATE).getString("character", "clawd");
        RadioGroup group = new RadioGroup(this);
        RadioButton clawd = new RadioButton(this);
        clawd.setText("Clawd");
        clawd.setId(1);
        RadioButton tung = new RadioButton(this);
        tung.setText("Tung Tung Tung Sahur");
        tung.setId(2);
        group.addView(clawd);
        group.addView(tung);
        group.check("tung".equals(saved) ? 2 : 1);
        group.setOnCheckedChangeListener((g, id) -> getSharedPreferences("pet", MODE_PRIVATE).edit()
                .putString("character", id == 2 ? "tung" : "clawd").apply());

        Button start = new Button(this);
        start.setText("召喚 Clawd");
        start.setOnClickListener(v -> startPet());

        Button stop = new Button(this);
        stop.setText("讓它回家");
        stop.setOnClickListener(v -> stopService(new Intent(this, PetService.class)));

        root.addView(title);
        root.addView(hint);
        root.addView(group);
        root.addView(start);
        root.addView(stop);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "請先允許「顯示在其他應用程式上層」", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        String ch = getSharedPreferences("pet", MODE_PRIVATE).getString("character", "clawd");
        startForegroundService(new Intent(this, PetService.class).putExtra(PetService.EXTRA_CHARACTER, ch));
    }
}
