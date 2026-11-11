package com.babisnap.grouptest;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

/** How to turn on the accessibility service for this sideloaded test app. */
public class AutoSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_settings);
        ((TextView) findViewById(R.id.settings_body)).setText(
                "האפליקציה הזו נפרדת מ־BabiSnap. היא שולחת רק בשיחה פרטית, ורק אחרי שסימנתם «בלי מגע».\n\n"
                        + "1. כבו זמנית את Play Protect, אחרת ההתקנה או הנגישות עלולות להיחסם:\n"
                        + "חנות Play ← אייקון החשבון ← Play Protect ← גלגל שיניים ← כבו «סרוק אפליקציות באמצעות Play Protect».\n"
                        + "אחרי שהשירות פועל אפשר להדליק את הסריקה בחזרה.\n\n"
                        + "2. באנדרואיד 13 ומעלה: הגדרות ← אפליקציות ← BabiSnap שליחה אוטומטית ← תפריט (⋮) ← Allow restricted settings (הגדרות מוגבלות).\n"
                        + "אם אין תפריט, נסו להפעיל את הנגישות פעם אחת וחזרו לפרטי האפליקציה.\n\n"
                        + "3. הגדרות ← נגישות ← אפליקציות שהותקנו ← BabiSnap שליחה אוטומטית ← הפעלה, ואשרו את האזהרה.\n\n"
                        + "השירות רואה רק מסכי WhatsApp, בודק כותרת ותיבת טקסט, ולוחץ שלח לכל היותר 10 שניות אחרי שהכרטיס נגמר. אין מאזין התראות ואין חלון מעל אפליקציות אחרות.");
        findViewById(R.id.open_a11y).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (ActivityNotFoundException e) {
                ProbeLog.code("SETTINGS_MISSING");
            }
        });
    }
}
