package net.kdt.pojavlaunch;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Minimal Tools shim for the vendored Pojav/Zalith input classes.
 * GPL-3.0-or-later — ObsiLauncher port.
 */
public class Tools {
    /** The display metrics of the current activity, set by the game screen. */
    public static volatile DisplayMetrics currentDisplayMetrics = new DisplayMetrics();

    /** Home JRE sub-directory inside a runtime (resolved at runtime, Pojav layout). */
    public static volatile String DIRNAME_HOME_JRE = "lib";

    public static int dpToPx(float dp) {
        return (int) (dp * currentDisplayMetrics.density + 0.5f);
    }

    public static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Get a display-friendly resolution value: scales the physical pixels by the
     * given scale factor (1 = native, 0.5 = half) and rounds to even.
     */
    public static int getDisplayFriendlyRes(int resolution, float scaleFactor) {
        int scaled = (int) (resolution * scaleFactor);
        if (scaled < 1) return 1;
        return scaled % 2 == 0 ? scaled : scaled + 1;
    }

    /** Accepts the game width/height scale used across the ported code (0.5 = half res). */
    public static float getDisplayScale(float requested) {
        if (requested <= 0f) return 1f;
        return Math.max(0.25f, Math.min(1f, requested));
    }
}
