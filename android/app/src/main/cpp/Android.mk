# ObsiLauncher JNI bridge — GPL-3.0-or-later
# Spawns the game JVM: fork + execve("/system/bin/linker64", [linker, launcher.so, ...]) .
# The parent keeps the Android runtime alive and monitors the child through waitpid.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := obsibridge
LOCAL_SRC_FILES := obsibridge.c
LOCAL_CFLAGS := -O2 -Wall
include $(BUILD_SHARED_LIBRARY)
