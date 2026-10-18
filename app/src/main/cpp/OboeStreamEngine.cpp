#include "OboeStreamEngine.h"
#include <android/log.h>
#include <chrono>

#define LOG_TAG "SpatialFlowOboe"

// Ring buffer capacity: 500ms of audio at maximum resolution (384kHz, 32-bit float/int, stereo)
// 384000 samples/sec * 8 bytes/frame * 0.5s = ~1,536,000 bytes
static constexpr size_t DEFAULT_RING_BUFFER_CAPACITY = 1536000;

OboeStreamEngine::OboeStreamEngine()
    : mRingBuffer(std::make_unique<LockFreeRingBuffer>(DEFAULT_RING_BUFFER_CAPACITY)) {}

OboeStreamEngine::~OboeStreamEngine() {
    close();
}

bool OboeStreamEngine::openStream(int32_t deviceId, int32_t sampleRate, int32_t channels, int32_t bitDepth) {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    closeStreamInternal();

    mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
    mChannelCount = (channels > 0) ? channels : 2;
    mBitDepth = bitDepth;

    oboe::AudioFormat oboeFormat = oboe::AudioFormat::I16;
    size_t bytesPerSample = 2;

    switch (bitDepth) {
        case 24:
            oboeFormat = oboe::AudioFormat::I24;
            bytesPerSample = 3; // 24-bit packed integer (3 bytes)
            break;
        case 32:
            oboeFormat = oboe::AudioFormat::I32;
            bytesPerSample = 4; // 32-bit integer (4 bytes)
            break;
        case -32: // Special token for 32-bit float
            oboeFormat = oboe::AudioFormat::Float;
            bytesPerSample = 4; // 32-bit float (4 bytes)
            break;
        case 16:
        default:
            oboeFormat = oboe::AudioFormat::I16;
            bytesPerSample = 2; // 16-bit integer (2 bytes)
            mBitDepth = 16;
            break;
    }

    mBytesPerFrame = mChannelCount * bytesPerSample;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive); // Prefer AudioFlinger bypass / direct MMAP
    builder.setAudioApi(oboe::AudioApi::AAudio);           // Prefer AAudio MMAP
    builder.setUsage(oboe::Usage::Media);
    builder.setContentType(oboe::ContentType::Music);

    if (deviceId > 0) {
        builder.setDeviceId(deviceId); // Lock to USB DAC hardware endpoint
    }

    builder.setSampleRate(mSampleRate);
    builder.setChannelCount(mChannelCount);
    builder.setFormat(oboeFormat);

    // Strict Audiophile Bit-Perfect Guarantees:
    // Reject any software sample rate conversion or channel/format remapping
    builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None);
    builder.setFormatConversionAllowed(false);
    builder.setChannelConversionAllowed(false);

    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    oboe::Result result = builder.openStream(mStream);
    mExclusiveOpenResult = result;
    if (result != oboe::Result::OK && deviceId > 0) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "Failed to open Exclusive stream on deviceId %d (%s). Retrying with unspecified device...",
                            deviceId, oboe::convertToText(result));
        builder.setDeviceId(oboe::kUnspecified);
        result = builder.openStream(mStream);
        mExclusiveOpenResult = result;
    }

    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "Failed to open Exclusive stream (%s). Trying Shared fallback...",
                            oboe::convertToText(result));

        // If Exclusive mode is rejected by the HAL (e.g. USB DAC kernel lacks MMAP), fall back to Shared AAudio on same device
        builder.setSharingMode(oboe::SharingMode::Shared);
        if (deviceId > 0) builder.setDeviceId(deviceId);
        result = builder.openStream(mStream);
        if (result != oboe::Result::OK && deviceId > 0) {
            builder.setDeviceId(oboe::kUnspecified);
            result = builder.openStream(mStream);
        }

        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                "Failed to open Oboe stream: %s",
                                oboe::convertToText(result));
            mStream = nullptr;
            mLastOpenResult = result;
            return false;
        }
    }

    mLastOpenResult = result;

    mRingBuffer->reset();
    mTotalFramesWritten.store(0, std::memory_order_relaxed);
    mTotalFramesRendered.store(0, std::memory_order_relaxed);
    mUnderflowCount.store(0, std::memory_order_relaxed);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Opened Oboe Stream: %d Hz, %d channels, %d-bit, API: %s, Sharing: %s, Burst: %d",
                        mStream->getSampleRate(),
                        mStream->getChannelCount(),
                        mBitDepth,
                        (mStream->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES"),
                        (mStream->getSharingMode() == oboe::SharingMode::Exclusive ? "Exclusive" : "Shared"),
                        mStream->getFramesPerBurst());

    return true;
}

size_t OboeStreamEngine::writePcm(const uint8_t* data, size_t bytes) {
    if (!data || bytes == 0 || !mRingBuffer) return 0;

    size_t written = mRingBuffer->write(data, bytes);
    if (written > 0 && mBytesPerFrame > 0) {
        mTotalFramesWritten.fetch_add(written / mBytesPerFrame, std::memory_order_relaxed);
    }
    return written;
}

bool OboeStreamEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream) return false;
    oboe::Result result = mStream->requestStart();
    return result == oboe::Result::OK;
}

bool OboeStreamEngine::pause() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream) return false;
    oboe::Result result = mStream->requestPause();
    return result == oboe::Result::OK;
}

bool OboeStreamEngine::flush() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mRingBuffer) {
        mRingBuffer->reset();
    }
    if (!mStream) return false;

    oboe::StreamState state = mStream->getState();
    if (state == oboe::StreamState::Pausing) {
        oboe::StreamState nextState = state;
        mStream->waitForStateChange(oboe::StreamState::Pausing, &nextState, 50 * oboe::kNanosPerMillisecond);
        state = mStream->getState();
    }

    if (state == oboe::StreamState::Paused || state == oboe::StreamState::Stopped || state == oboe::StreamState::Open) {
        oboe::Result result = mStream->requestFlush();
        return result == oboe::Result::OK;
    }
    return true; // Ring buffer flushed; stream active or transitioning
}

bool OboeStreamEngine::stop() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream) return false;
    oboe::Result result = mStream->requestStop();
    return result == oboe::Result::OK;
}

void OboeStreamEngine::close() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    closeStreamInternal();
}

void OboeStreamEngine::closeStreamInternal() {
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
    if (mRingBuffer) {
        mRingBuffer->reset();
    }
}

bool OboeStreamEngine::hasDrained() const noexcept {
    if (!mRingBuffer) return true;
    return mRingBuffer->getAvailableRead() == 0;
}

int64_t OboeStreamEngine::getHardwareTimestampUs() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mStream || mSampleRate <= 0) return 0;

    int64_t framePosition = 0;
    int64_t timeNanoseconds = 0;

    // Interrogate hardware clock directly via AAudio timestamp
    oboe::Result result = mStream->getTimestamp(CLOCK_MONOTONIC, &framePosition, &timeNanoseconds);
    if (result == oboe::Result::OK && framePosition >= 0) {
        return (framePosition * 1000000LL) / mSampleRate;
    }

    // Fallback: estimate from total rendered frames
    int64_t rendered = mTotalFramesRendered.load(std::memory_order_relaxed);
    return (rendered * 1000000LL) / mSampleRate;
}

oboe::DataCallbackResult OboeStreamEngine::onAudioReady(
    oboe::AudioStream * /* audioStream */,
    void *audioData,
    int32_t numFrames
) {
    if (__builtin_expect(!audioData || numFrames <= 0, 0)) {
        return oboe::DataCallbackResult::Continue;
    }

    const size_t requestedBytes = static_cast<size_t>(numFrames) * mBytesPerFrame;
    uint8_t* out = static_cast<uint8_t*>(audioData);

    const size_t bytesRead = mRingBuffer->read(out, requestedBytes);

    if (__builtin_expect(bytesRead < requestedBytes, 0)) {
        // Underflow: zero-pad remainder to prevent loud clicks or audio bursts
        std::memset(out + bytesRead, 0, requestedBytes - bytesRead);
        mUnderflowCount.fetch_add(1, std::memory_order_relaxed);
    }

    mTotalFramesRendered.fetch_add(numFrames, std::memory_order_relaxed);
    return oboe::DataCallbackResult::Continue;
}

void OboeStreamEngine::onErrorAfterClose(
    oboe::AudioStream * /* audioStream */,
    oboe::Result error
) {
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                        "Oboe stream closed due to error/disconnect: %s",
                        oboe::convertToText(error));

    if (mDisconnectCallback) {
        mDisconnectCallback(static_cast<int32_t>(error));
    }
}

const char* OboeStreamEngine::getLastOpenResultText() const noexcept {
    if (mStream) {
        if (mStream->getSharingMode() == oboe::SharingMode::Exclusive) {
            return "OK (Exclusive MMAP Bit-Exact)";
        } else {
            static char sMsg[128];
            snprintf(sMsg, sizeof(sMsg), "Shared OK (Exclusive rejected by HAL: %s)", oboe::convertToText(mExclusiveOpenResult));
            return sMsg;
        }
    }
    return oboe::convertToText(mLastOpenResult);
}
