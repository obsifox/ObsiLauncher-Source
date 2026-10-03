package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.view.Surface;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Slim JREUtils: the native declarations the vendored Pojav/Zalith JNI expects,
 * plus the dlopen/ld-path helpers used by the ObsiLauncher in-process JVM launch.
 * GPL-3.0-or-later — ObsiLauncher port (from PojavLauncher/ZalithLauncher).
 */
public class JREUtils {
    public static String LD_LIBRARY_PATH = "";
    public static String jvmLibraryPath = "";

    // ---- native declarations (implemented in the vendored pojav JNI) ----
    public static native boolean dlopen(String name);
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native int chdir(String name);
    public static native void setupBridgeWindow(Surface surface);
    public static native void releaseBridgeWindow();
    public static native void setupBridgeSurfaceAWT(long surfacePtr);
    public static native void initializeGameExitHook();
    public static native void setupExitMethod(Context context);
    public static native int executeBinary(String[] args);
    public static native int executeForkedAndExitedBinary(String[] args);

    /** Set LD_LIBRARY_PATH both for the JVM we boot and for our dlopen lookups. */
    public static void setLdLibPath(String path) {
        LD_LIBRARY_PATH = path;
        setLdLibraryPath(path);
    }

    /** Resolve a bare library name against LD_LIBRARY_PATH, like Zalith does. */
    public static String findInLdLibPath(String libName) {
        for (String path : LD_LIBRARY_PATH.split(":")) {
            if (path.isEmpty()) continue;
            File f = new File(path, libName);
            if (f.exists()) return f.getAbsolutePath();
        }
        return libName;
    }

    /** Boot-strap all JVM shared libraries (Zalith initJavaRuntime equivalent). */
    public static void initJavaRuntime(String jreHome) {
        dlopen(findInLdLibPath("libjli.so"));
        if (!dlopen("libjvm.so")) {
            Logger.appendToLog("DynamicLoader: failed plain libjvm.so load, trying full path");
            dlopen(jvmLibraryPath + "/libjvm.so");
        }
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        // best-effort: load every remaining native lib shipped with the JRE
        for (File f : locateLibs(new File(jreHome, Tools.DIRNAME_HOME_JRE))) {
            dlopen(f.getAbsolutePath());
        }
    }

    private static List<File> locateLibs(File dir) {
        List<File> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".so")) out.add(f);
            else if (f.isDirectory()) out.addAll(locateLibs(f));
        }
        return out;
    }

    /**
     * Stream the JRE-side logcat tags (jrelog/LIBGL/NativeInput) to a listener.
     * The vendored stdio_is redirects the JVM stdout/stderr to these tags.
     */
    public static Thread startLogcatReader(String[] tags, java.util.function.Consumer<String> onLine) {
        Thread t = new Thread(() -> {
            try {
                java.lang.Process p;
                try {
                    new ProcessBuilder("logcat", "-c").redirectErrorStream(true).start().waitFor();
                } catch (Exception ignored) {
                }
                List<String> cmd = new ArrayList<>();
                cmd.add("logcat");
                cmd.add("-v");
                cmd.add("brief");
                cmd.add("-s");
                for (String tag : tags) cmd.add(tag + ":I");
                p = new ProcessBuilder().command(cmd).redirectErrorStream(true).start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        onLine.accept(line + "\n");
                    }
                }
            } catch (Exception e) {
                Logger.appendToLog("logcat reader failed: " + e);
            }
        }, "obsi-jre-logcat");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Debug helper: print the device GLES version. */
    @SuppressWarnings("unused")
    public static int getDetectedVersion() {
        try {
            return Integer.parseInt(android.opengl.GLES10.glGetString(android.opengl.GLES10.GL_VERSION)
                    .replaceAll("[^0-9]*([0-9.]+).*", "$1").trim().split("\\.")[0]);
        } catch (Throwable t) {
            return 3;
        }
    }

    public static String archLibDir() {
        boolean is64 = android.os.Build.SUPPORTED_64_BIT_ABIS != null
                && android.os.Build.SUPPORTED_64_BIT_ABIS.length > 0;
        if (!is64) return "arm";
        String abi = android.os.Build.SUPPORTED_64_BIT_ABIS[0];
        if (abi.toLowerCase(Locale.ROOT).contains("x86")) return "x86_64";
        return "arm64";
    }
}
