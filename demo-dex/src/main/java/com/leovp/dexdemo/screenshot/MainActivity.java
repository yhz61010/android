package com.leovp.dexdemo.screenshot;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.leovp.dexdemo.R;

/**
 * https://github.com/rayworks/DroidCast
 */
public class MainActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
         * https://developer.android.google.cn/about/versions/oreo/android-8.0-changes.html
         * #security-all
         */
        ApplicationInfo info = getApplicationInfo();
        String srcLocation = info.sourceDir;

        TextView textView = findViewById(R.id.text);
        textView.setText("Source apk Dir:" + srcLocation);
    }
}
