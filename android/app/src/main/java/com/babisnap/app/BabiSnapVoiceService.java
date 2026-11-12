package com.babisnap.app;

import android.service.voice.VoiceInteractionService;

/** Lets Settings list BabiSnap as the digital assistant. The session starts the normal recording. */
public class BabiSnapVoiceService extends VoiceInteractionService {
    @Override public void onReady() { super.onReady(); }
}
