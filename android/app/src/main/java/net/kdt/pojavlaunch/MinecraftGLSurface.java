package net.kdt.pojavlaunch;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Minimal stand-in for PojavLauncher's MinecraftGLSurface: the vendored input
 * stack only needs the Android->GLFW mouse-button conversion entry point.
 * GPL-3.0-or-later — ObsiLauncher port (from PojavLauncher/ZalithLauncher).
 */
public final class MinecraftGLSurface {
    private MinecraftGLSurface() {}

    /** Convert an android MotionEvent action button value and forward it. */
    public static boolean sendMouseButtonUnconverted(int androidButton, boolean isDown) {
        int glfwButton;
        switch (androidButton) {
            case android.view.MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
            case android.view.MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case android.view.MotionEvent.BUTTON_PRIMARY:
            default:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
        }
        CallbackBridge.sendMouseButton(glfwButton, isDown);
        return true;
    }
}
