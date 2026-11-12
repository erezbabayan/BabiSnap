package com.babisnap.app;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class BabiSnapSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args) {
        return new BabiSnapSession(this);
    }
}
