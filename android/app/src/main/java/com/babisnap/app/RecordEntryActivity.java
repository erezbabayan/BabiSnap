package com.babisnap.app;

import android.app.Activity;
import android.os.Bundle;

/**
 * «BabiSnap הקלטה», plus ASSIST and VOICE_COMMAND. Always the recording screen,
 * including a second side-key press while this entry is already alive.
 */
public class RecordEntryActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        openRecording();
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRecording();
    }

    private void openRecording() {
        startActivity(RecordLaunch.recording(this));
        finish();
    }
}
