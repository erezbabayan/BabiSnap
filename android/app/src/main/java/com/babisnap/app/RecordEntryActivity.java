package com.babisnap.app;

import android.app.Activity;
import android.os.Bundle;

/**
 * Launcher entry «BabiSnap הקלטה» for a side-button double press, and the ASSIST / VOICE_COMMAND
 * activity. Starts the home-button recording, or Settings when the microphone is not granted.
 */
public class RecordEntryActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        startActivity(RecordLaunch.intent(this));
        finish();
    }
}
