package com.babisnap.app;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.util.UUID;

/**
 * Short voice file in the app cache, for a WhatsApp share. Never logs the transcript or the path.
 * Opus/ogg when the device can encode it; otherwise m4a. The file is deleted on cancel, expiry, or after share.
 */
final class WhatsAppCapture {
    static final String DIR = "wa";
    private static final long SWEEP_MS = 15 * 60 * 1000L;
    private static final long MIN_BYTES = 200;

    private MediaRecorder rec;
    private File file;
    private String mime = "audio/mp4";

    /** @return true when the microphone is recording into a cache file. */
    boolean start(Context c) {
        File dir = new File(c.getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        String id = UUID.randomUUID().toString();
        if (WhatsAppLogic.useOpus(Build.VERSION.SDK_INT)
                && tryStart(c, new File(dir, id + ".ogg"), true)) return true;
        return tryStart(c, new File(dir, id + ".m4a"), false);
    }

    private boolean tryStart(Context c, File out, boolean opus) {
        MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(c) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            if (opus) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
                mime = "audio/ogg";
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mime = "audio/mp4";
            }
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16000);
            r.setAudioEncodingBitRate(32000);
            r.setOutputFile(out.getAbsolutePath());
            r.prepare();
            r.start();
            rec = r;
            file = out;
            return true;
        } catch (Exception ignored) {
            try { r.release(); } catch (Exception ignored2) {}
            if (out.exists() && !out.delete()) out.deleteOnExit();
            file = null;
            return false;
        }
    }

    boolean isRecording() { return rec != null; }

    /** Stop the encoder and keep the file when it actually contains audio. */
    void stop() {
        MediaRecorder r = rec;
        rec = null;
        if (r == null) return;
        try { r.stop(); } catch (Exception ignored) { deleteFile(); }
        try { r.release(); } catch (Exception ignored) {}
        if (file != null && file.length() < MIN_BYTES) deleteFile();
    }

    boolean ready() { return rec == null && file != null && file.isFile() && file.length() >= MIN_BYTES; }
    File file() { return file; }
    String mime() { return mime; }

    void delete() {
        MediaRecorder r = rec;
        rec = null;
        if (r != null) {
            try { r.reset(); } catch (Exception ignored) {}
            try { r.release(); } catch (Exception ignored) {}
        }
        deleteFile();
    }

    private void deleteFile() {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
        file = null;
    }

    /** Drop orphaned share files. Leaves a file that was just written (WhatsApp may still be reading it). */
    static void sweep(File cacheDir) {
        File dir = new File(cacheDir, DIR);
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - SWEEP_MS;
        for (File f : files) if (f.lastModified() < cutoff && !f.delete()) f.deleteOnExit();
    }

    static void deleteAll(Context c) {
        File dir = new File(c.getCacheDir(), DIR);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (!f.delete()) f.deleteOnExit();
        if (dir.exists() && !dir.delete()) dir.deleteOnExit();
    }
}
