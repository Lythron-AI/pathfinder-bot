/**
 * jni_bridge.cpp
 *
 * JNI interface between the Android app (Kotlin) and the pathfinder A* engine.
 *
 * Kotlin side signature:
 *   external fun pathfindNative(lvlString: String): ByteArray
 *   external fun pathfindNativeAsync(lvlString: String, callbackId: Long): Unit
 *   (progress delivered via PathfinderEngine.onProgress(callbackId, percent))
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <functional>
#include <future>
#include <string>
#include <vector>

// Declared in pathfinder_android.cpp
std::vector<uint8_t> pathfind_android(
    const std::string& lvlString,
    std::atomic_bool& stop,
    std::function<void(double)> callback);

#define LOG_TAG "PathfinderJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Async state ───────────────────────────────────────────────────────────────
static std::atomic_bool g_stop{false};
static std::future<void> g_future;

extern "C" {

/**
 * Synchronous pathfind — blocks the calling thread until done.
 * Call from a background thread in Kotlin.
 *
 * Returns: raw .gdr2 bytes, or empty array on failure.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_pathfinder_gd_PathfinderEngine_pathfindSync(
    JNIEnv* env, jclass /*clazz*/,
    jstring lvlStringJ)
{
    const char* lvlCStr = env->GetStringUTFChars(lvlStringJ, nullptr);
    std::string lvlString(lvlCStr);
    env->ReleaseStringUTFChars(lvlStringJ, lvlCStr);

    std::atomic_bool stop{false};
    LOGI("pathfindSync: starting, level len=%zu", lvlString.size());

    std::vector<uint8_t> result;
    try {
        result = pathfind_android(lvlString, stop, [](double pct) {
            LOGI("progress: %.1f%%", pct);
        });
    } catch (const std::exception& e) {
        LOGE("pathfindSync exception: %s", e.what());
        return env->NewByteArray(0);
    }

    LOGI("pathfindSync: done, %zu bytes", result.size());
    jbyteArray out = env->NewByteArray(static_cast<jsize>(result.size()));
    if (!result.empty())
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(result.size()),
                                reinterpret_cast<const jbyte*>(result.data()));
    return out;
}

/**
 * Async pathfind — starts a native thread.
 * Progress and completion are posted back via JNI callbacks to PathfinderEngine.
 *
 * [callbackId] is an opaque long passed back to Kotlin so it can correlate results.
 */
JNIEXPORT void JNICALL
Java_com_pathfinder_gd_PathfinderEngine_pathfindAsync(
    JNIEnv* env, jclass clazz,
    jstring lvlStringJ, jlong callbackId)
{
    // Stop any previous run
    g_stop = true;
    if (g_future.valid()) g_future.wait();
    g_stop = false;

    const char* lvlCStr = env->GetStringUTFChars(lvlStringJ, nullptr);
    std::string lvlString(lvlCStr);
    env->ReleaseStringUTFChars(lvlStringJ, lvlCStr);

    // Get the JVM so we can attach the native thread
    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);

    // Keep a global ref to PathfinderEngine class for callbacks
    jclass engineClassLocal = env->FindClass("com/pathfinder/gd/PathfinderEngine");
    jclass engineClass = static_cast<jclass>(env->NewGlobalRef(engineClassLocal));

    LOGI("pathfindAsync: starting id=%lld", callbackId);

    g_future = std::async(std::launch::async,
        [jvm, engineClass, lvlString, callbackId]() mutable {
            JNIEnv* tenv = nullptr;
            JavaVMAttachArgs args{JNI_VERSION_1_6, const_cast<char*>("PathfinderThread"), nullptr};
            jvm->AttachCurrentThread(&tenv, &args);

            jmethodID onProgressId = tenv->GetStaticMethodID(
                engineClass, "onProgress", "(JD)V");
            jmethodID onDoneId = tenv->GetStaticMethodID(
                engineClass, "onDone", "(J[B)V");

            std::vector<uint8_t> result;
            try {
                result = pathfind_android(lvlString, g_stop,
                    [tenv, engineClass, onProgressId, callbackId](double pct) {
                        tenv->CallStaticVoidMethod(
                            engineClass, onProgressId,
                            static_cast<jlong>(callbackId),
                            static_cast<jdouble>(pct));
                    });
            } catch (const std::exception& e) {
                LOGE("pathfindAsync exception: %s", e.what());
            }

            jbyteArray out = tenv->NewByteArray(static_cast<jsize>(result.size()));
            if (!result.empty())
                tenv->SetByteArrayRegion(out, 0, static_cast<jsize>(result.size()),
                                         reinterpret_cast<const jbyte*>(result.data()));

            tenv->CallStaticVoidMethod(
                engineClass, onDoneId,
                static_cast<jlong>(callbackId), out);

            tenv->DeleteGlobalRef(engineClass);
            jvm->DetachCurrentThread();
        });
}

/**
 * Cancel any running async pathfind.
 */
JNIEXPORT void JNICALL
Java_com_pathfinder_gd_PathfinderEngine_cancelPathfind(
    JNIEnv* /*env*/, jclass /*clazz*/)
{
    g_stop = true;
    LOGI("cancelPathfind called");
}

} // extern "C"
