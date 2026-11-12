package com.babisnap.app;

import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

/** Long-press assistant. Does not read the screen, and starts the same recording as the home button. */
public class BabiSnapSession extends VoiceInteractionSession {
    public BabiSnapSession(Context context) { super(context); }

    @Override public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Context c = getContext();
        try { startVoiceActivity(RecordLaunch.intent(c)); }
        catch (RuntimeException e) { c.startActivity(RecordLaunch.intent(c)); }
        hide();
    }
}
