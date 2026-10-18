#include <jni.h>
#include <android/log.h>
#include "OboeStreamEngine.h"

#define LOG_TAG "SpatialFlowOboeJNI"

static OboeStreamEngine* gEngine = nullptr;
static JavaVM* gJavaVM = nullptr;
static jobject gDisconnectCallbackObject = nullptr;
static jmethodID gOnDisconnectMethodId = nullptr;

jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* /* vm */, void* /* reserved */) {
    if (gEngine) {
        delete gEngine;
        gEngine = nullptr;
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeInit(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) {
        gEngine = new OboeStreamEngine();
        gEngine->setDisconnectCallback([](int32_t errorCode) {
            if (!gJavaVM || !gDisconnectCallbackObject || !gOnDisconnectMethodId) return;

            JNIEnv* env = nullptr;
            jint res = gJavaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
            bool attached = false;

            if (res == JNI_EDETACHED) {
                if (gJavaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                    attached = true;
                }
            }

            if (env) {
                env->CallVoidMethod(gDisconnectCallbackObject, gOnDisconnectMethodId, errorCode);
                if (attached) {
                    gJavaVM->DetachCurrentThread();
                }
            }
        });
    }
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeOpenStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint deviceId,
    jint sampleRate,
    jint channels,
    jint bitDepth
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->openStream(deviceId, sampleRate, channels, bitDepth) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeWriteDirectBuffer(
    JNIEnv* env,
    jobject /* thiz */,
    jobject directByteBuffer,
    jint offsetBytes,
    jint lengthBytes
) {
    if (!gEngine || !directByteBuffer || lengthBytes <= 0) return 0;

    uint8_t* bufferPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(directByteBuffer));
    if (!bufferPtr) return 0;

    return static_cast<jint>(gEngine->writePcm(bufferPtr + offsetBytes, static_cast<size_t>(lengthBytes)));
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeWriteByteArray(
    JNIEnv* env,
    jobject /* thiz */,
    jbyteArray byteArray,
    jint offset,
    jint length
) {
    if (!gEngine || !byteArray || length <= 0) return 0;

    jbyte* bytes = env->GetByteArrayElements(byteArray, nullptr);
    if (!bytes) return 0;

    size_t written = gEngine->writePcm(reinterpret_cast<const uint8_t*>(bytes + offset), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(byteArray, bytes, JNI_ABORT);

    return static_cast<jint>(written);
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeStart(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativePause(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->pause() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeFlush(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->flush() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeStop(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->stop() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeClose(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (gEngine) {
        gEngine->close();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeHasDrained(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_TRUE;
    return gEngine->hasDrained() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetHardwareTimestampUs(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return static_cast<jlong>(gEngine->getHardwareTimestampUs());
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetSampleRate(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getSampleRate();
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetChannelCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getChannelCount();
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetBitDepth(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getBitDepth();
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeIsStreamOpen(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->isStreamOpen() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeIsExclusive(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return JNI_FALSE;
    return gEngine->isExclusive() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetSharingMode(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gEngine) return env->NewStringUTF("Closed");
    return env->NewStringUTF(gEngine->getSharingModeName());
}

JNIEXPORT jstring JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetAudioApi(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gEngine) return env->NewStringUTF("None");
    return env->NewStringUTF(gEngine->getAudioApiName());
}

JNIEXPORT jstring JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetLastError(
    JNIEnv* env,
    jobject /* thiz */
) {
    if (!gEngine) return env->NewStringUTF("Engine not initialized");
    return env->NewStringUTF(gEngine->getLastOpenResultText());
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetActualSampleRate(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getActualSampleRate();
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetFramesPerBurst(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getFramesPerBurst();
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetBufferSizeInFrames(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return gEngine->getBufferSizeInFrames();
}

JNIEXPORT jint JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetUnderflowCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return static_cast<jint>(gEngine->getUnderflowCount());
}

JNIEXPORT jlong JNICALL
Java_com_codetrio_spatialflow_service_dac_NativeOboeEngine_nativeGetTotalFramesRendered(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    if (!gEngine) return 0;
    return static_cast<jlong>(gEngine->getTotalFramesRendered());
}

} // extern "C"
