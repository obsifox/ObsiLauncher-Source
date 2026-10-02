/*
 * ObsiLauncher JNI bridge — GPL-3.0-or-later
 *
 * fork() + execve("/system/bin/linker64", [linker, launcher.so, ...argv], envp).
 * Executing system binaries stays allowed on Android 10+ while exec() of
 * app-data files does not; the system linker then loads the pack's JVM
 * launcher shared object. stdout/stderr of the child are redirected into a
 * pipe that the Kotlin side reads on a background thread.
 */
#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <fcntl.h>

/* read end of the last child log pipe */
static int g_last_log_fd = -1;

static char **copy_string_array(JNIEnv *env, jobjectArray array, size_t *out_len) {
    jsize n = (*env)->GetArrayLength(env, array);
    char **out = calloc((size_t) n + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
        out[i] = strdup(utf ? utf : "");
        (*env)->ReleaseStringUTFChars(env, js, utf);
        (*env)->DeleteLocalRef(env, js);
    }
    *out_len = (size_t) n;
    return out;
}

static void free_string_array(char **array) {
    if (array == NULL) return;
    for (size_t i = 0; array[i] != NULL; i++) free(array[i]);
    free(array);
}

JNIEXPORT jint JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_forkAndExec(
        JNIEnv *env, jclass clazz,
        jstring jLinker, jstring jSo, jobjectArray jArgv, jobjectArray jEnvp) {
    (void) clazz;

    const char *linker = (*env)->GetStringUTFChars(env, jLinker, NULL);
    const char *so = (*env)->GetStringUTFChars(env, jSo, NULL);

    size_t argv_len = 0, env_len = 0;
    char **argv = copy_string_array(env, jArgv, &argv_len);
    char **envp = copy_string_array(env, jEnvp, &env_len);

    /* final argv: [linker, so, ...argv] */
    char **full_argv = calloc(argv_len + 3, sizeof(char *));
    full_argv[0] = strdup(linker);
    full_argv[1] = strdup(so);
    for (size_t i = 0; i < argv_len; i++) full_argv[i + 2] = argv[i];

    /* parent <-> child log pipe */
    int logfds[2];
    int pipe_ok = pipe2(logfds, O_CLOEXEC) == 0;

    pid_t pid = fork();
    if (pid == 0) {
        /* child */
        if (pipe_ok) {
            dup2(logfds[1], STDOUT_FILENO);
            dup2(logfds[1], STDERR_FILENO);
            dup2(logfds[1], STDIN_FILENO);
        }
        signal(SIGPIPE, SIG_DFL);
        execve(full_argv[0], full_argv, envp);
        /* execve failed */
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, jLinker, linker);
    (*env)->ReleaseStringUTFChars(env, jSo, so);
    free_string_array(argv);
    free_string_array(envp);
    free_string_array(full_argv);

    if (pipe_ok) close(logfds[1]);
    if (pid < 0) {
        if (pipe_ok) close(logfds[0]);
        return -1;
    }

    /* expose the read end to Kotlin through a well-known env-free channel:
       the caller passes a descriptor via wait-free design — we simply stash it */
    if (pipe_ok) {
        if (g_last_log_fd >= 0) close(g_last_log_fd);
        g_last_log_fd = logfds[0];
    }
    return (jint) pid;
}

JNIEXPORT jint JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_takeLogFd(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    jint fd = (jint) g_last_log_fd;
    g_last_log_fd = -1;
    return fd;
}

JNIEXPORT void JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_kill(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid > 0) kill((pid_t) pid, SIGKILL);
}

JNIEXPORT jint JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_waitPid(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return -1;
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_chdir(JNIEnv *env, jclass clazz, jstring jPath) {
    (void) clazz;
    const char *path = (*env)->GetStringUTFChars(env, jPath, NULL);
    chdir(path);
    (*env)->ReleaseStringUTFChars(env, jPath, path);
}

JNIEXPORT void JNICALL
Java_studio_obsifox_obsilauncher_core_jni_ObsiBridge_setenv(JNIEnv *env, jclass clazz, jstring jKey, jstring jValue) {
    (void) clazz;
    const char *key = (*env)->GetStringUTFChars(env, jKey, NULL);
    const char *value = (*env)->GetStringUTFChars(env, jValue, NULL);
    setenv(key, value, 1);
    (*env)->ReleaseStringUTFChars(env, jKey, key);
    (*env)->ReleaseStringUTFChars(env, jValue, value);
}
