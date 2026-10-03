# ObsiLauncher native build — GPL-3.0-or-later
#
#  obsibridge      : our own tiny bridge (fork/exec helpers kept for headless JVM runs)
#  driver_helper   : Pojav/Zalith GPU-driver namespace helper
#  pojavexec       : Pojav/Zalith JVM host (JLI_Launch), EGL/ctx bridges, input bridge,
#                    stdio redirection — the game JVM runs IN this process
#  exithook        : bytehook-based exit() interception so a dying game returns
#                    control to the launcher instead of killing the app
#  linkerhook      : fake linker namespace so JVM libs in app data can be dlopened
#  pojavexec_awt   : AWT input bridge (caciocavallo)
#  awt_xawt        : fake xawt for headless AWT
#
# Vendored from ZalithLauncher (GPL-3.0) / PojavLauncher (GPL-3.0), see NOTICE.md.

LOCAL_PATH := $(call my-dir)
HERE_PATH := $(LOCAL_PATH)

POJAV_PATH := $(LOCAL_PATH)/pojav

# ---------------- obsibridge (ours) ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := obsibridge
LOCAL_SRC_FILES := obsibridge.c
LOCAL_CFLAGS := -O2 -Wall -D_GNU_SOURCE
include $(BUILD_SHARED_LIBRARY)

# ---------------- driver_helper ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := driver_helper
LOCAL_C_INCLUDES := $(HERE_PATH)   # GL/gl.h (vendored Mesa header, Zalith parity)
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_CFLAGS += -g -rdynamic
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
LOCAL_LDLIBS += -lEGL -lGLESv2
endif
LOCAL_SRC_FILES := pojav/driver_helper/driver_helper.c pojav/driver_helper/nsbypass.c
include $(BUILD_SHARED_LIBRARY)

# ---------------- pojavexec ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec
LOCAL_C_INCLUDES := $(HERE_PATH) $(HERE_PATH)/pojav $(HERE_PATH)/pojav/include
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_SHARED_LIBRARIES := driver_helper
LOCAL_CFLAGS += -rdynamic -D_GNU_SOURCE
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
LOCAL_LDLIBS += -lEGL -lGLESv2
endif
LOCAL_SRC_FILES := \
    pojav/bigcoreaffinity.c \
    pojav/egl_bridge.c \
    pojav/ctxbridges/br_loader.c \
    pojav/ctxbridges/gl_bridge.c \
    pojav/ctxbridges/osm_bridge.c \
    pojav/ctxbridges/egl_loader.c \
    pojav/ctxbridges/osmesa_loader.c \
    pojav/ctxbridges/swap_interval_no_egl.c \
    pojav/ctxbridges/virgl_bridge.c \
    pojav/environ/environ.c \
    pojav/input_bridge_v3.c \
    pojav/jre_launcher.c \
    pojav/utils.c \
    pojav/stdio_is.c \
    pojav/java_exec_hooks.c \
    pojav/lwjgl_dlopen_hook.c
include $(BUILD_SHARED_LIBRARY)

# ---------------- exithook ----------------
# bytehook comes from the com.bytedance:bytehook AAR via prefab (AGP exposes
# the import path automatically when buildFeatures.prefab = true)
$(call import-module,prefab/bytehook)

LOCAL_PATH := $(HERE_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := exithook
LOCAL_C_INCLUDES := $(HERE_PATH)/pojav/include
LOCAL_LDLIBS := -ldl -llog
LOCAL_SHARED_LIBRARIES := bytehook pojavexec
LOCAL_SRC_FILES := pojav/exit_hook.c
include $(BUILD_SHARED_LIBRARY)

# ---------------- linkerhook ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := linkerhook
LOCAL_LDLIBS := -ldl -llog
LOCAL_SRC_FILES := \
    pojav/linkerhook/linkerhook.cpp \
    pojav/linkerhook/linkerns.c
LOCAL_LDFLAGS := -z global
include $(BUILD_SHARED_LIBRARY)

# ---------------- pojavexec_awt ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec_awt
LOCAL_LDLIBS := -ldl -llog
LOCAL_SRC_FILES := pojav/awt_bridge.c
include $(BUILD_SHARED_LIBRARY)

# ---------------- fake awt libs ----------------
include $(CLEAR_VARS)
LOCAL_MODULE := awt_headless
include $(BUILD_SHARED_LIBRARY)

LOCAL_PATH := $(HERE_PATH)/pojav/awt_xawt
include $(CLEAR_VARS)
LOCAL_MODULE := awt_xawt
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_SHARED_LIBRARIES := awt_headless
LOCAL_LDLIBS := -ldl -llog
LOCAL_SRC_FILES := xawt_fake.c
include $(BUILD_SHARED_LIBRARY)

LOCAL_PATH := $(HERE_PATH)
