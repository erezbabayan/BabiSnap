package com.babisnap.app;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

/**
 * Required so the assistant entry is valid. It never opens the microphone:
 * the real recording is the normal VoiceActivity path.
 */
public class BabiSnapRecognition extends RecognitionService {
    @Override protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try { listener.error(SpeechRecognizer.ERROR_CLIENT); }
        catch (RemoteException ignored) {}
    }

    @Override protected void onCancel(Callback listener) {}

    @Override protected void onStopListening(Callback listener) {}
}
