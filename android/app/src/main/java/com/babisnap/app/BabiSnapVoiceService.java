package com.babisnap.app;

import android.os.Bundle;
import android.service.voice.VoiceInteractionService;

/** Digital assistant. A long press shows the session, which opens the recording screen. */
public class BabiSnapVoiceService extends VoiceInteractionService {
    @Override public void onReady() { super.onReady(); }

    @Override public void onLaunchVoiceAssistFromKeyguard() {
        try { showSession(new Bundle(), 0); }
        catch (RuntimeException e) {
            try { startActivity(RecordLaunch.recording(this)); }
            catch (RuntimeException ignored) {}
        }
    }
}
