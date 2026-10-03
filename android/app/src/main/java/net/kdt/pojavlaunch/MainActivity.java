package net.kdt.pojavlaunch;

import android.content.ClipData;

import studio.obsifox.obsilauncher.core.game.ObsiClipboardBridge;

/**
 * Native-facing shim the vendored awt_bridge.c binds to (JNI_OnLoad looks up
 * net/kdt/pojavlaunch/MainActivity + its static clipboard/link methods).
 * Delegates to the ObsiLauncher clipboard bridge and app-context holder.
 * GPL-3.0-or-later — ObsiLauncher port (from PojavLauncher/ZalithLauncher).
 */
public final class MainActivity {
    private MainActivity() {}

    public static void openLink(String link) {
        ObsiClipboardBridge.openLink(link);
    }

    public static void querySystemClipboard() {
        try {
            android.content.ClipboardManager cm = ObsiClipboardBridge.CLIPBOARD;
            if (cm == null) return;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() < 1) return;
            CharSequence text = cd.getItemAt(0).getText();
            if (text != null) {
                AWTInputBridge.nativeClipboardReceived(text.toString(), "text/plain");
            }
        } catch (Throwable ignored) {
        }
    }

    public static void putClipboardData(String data, String mimeType) {
        try {
            android.content.ClipboardManager cm = ObsiClipboardBridge.CLIPBOARD;
            if (cm == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("minecraft", data));
        } catch (Throwable ignored) {
        }
    }
}
