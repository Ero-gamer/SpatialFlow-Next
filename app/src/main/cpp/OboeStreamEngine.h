#pragma once

#include <oboe/Oboe.h>
#include <memory>
#include <mutex>
#include <atomic>
#include <functional>
#include "LockFreeRingBuffer.h"

class OboeStreamEngine : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    using DisconnectCallback = std::function<void(int32_t errorCode)>;

    OboeStreamEngine();
    ~OboeStreamEngine() override;

    // Disallow copy/move
    OboeStreamEngine(const OboeStreamEngine&) = delete;
    OboeStreamEngine& operator=(const OboeStreamEngine&) = delete;

    /**
     * Configures and opens a bit-perfect exclusive AAudio MMAP stream.
     * Guaranteed zero sample rate conversion and zero format alteration.
     */
    bool openStream(int32_t deviceId, int32_t sampleRate, int32_t channels, int32_t bitDepth);

    /**
     * Writes raw decoded PCM bytes into the lock-free ring buffer.
     */
    size_t writePcm(const uint8_t* data, size_t bytes);

    bool start();
    bool pause();
    bool flush();
    bool stop();
    void close();

    /**
     * Checks if the ring buffer has drained to empty.
     */
    bool hasDrained() const noexcept;

    /**
     * Returns the current playout timestamp in microseconds for Media3 A/V sync.
     */
    int64_t getHardwareTimestampUs();

    int32_t getSampleRate() const noexcept { return mSampleRate; }
    int32_t getActualSampleRate() const noexcept {
        return mStream ? mStream->getSampleRate() : mSampleRate;
    }
    int32_t getChannelCount() const noexcept { return mChannelCount; }
    int32_t getBitDepth() const noexcept { return mBitDepth; }
    bool isStreamOpen() const noexcept { return mStream != nullptr; }
    bool isExclusive() const noexcept {
        return mStream != nullptr && mStream->getSharingMode() == oboe::SharingMode::Exclusive;
    }
    const char* getSharingModeName() const noexcept {
        if (!mStream) return "Closed";
        return mStream->getSharingMode() == oboe::SharingMode::Exclusive ? "Exclusive" : "Shared";
    }
    const char* getAudioApiName() const noexcept {
        if (!mStream) return "None";
        return mStream->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSLES";
    }
    const char* getLastOpenResultText() const noexcept;
    int32_t getFramesPerBurst() const noexcept {
        return mStream ? mStream->getFramesPerBurst() : 0;
    }
    int32_t getBufferSizeInFrames() const noexcept {
        return mStream ? mStream->getBufferSizeInFrames() : 0;
    }
    uint32_t getUnderflowCount() const noexcept {
        return mUnderflowCount.load(std::memory_order_relaxed);
    }
    int64_t getTotalFramesRendered() const noexcept {
        return mTotalFramesRendered.load(std::memory_order_relaxed);
    }

    void setDisconnectCallback(DisconnectCallback callback) {
        mDisconnectCallback = std::move(callback);
    }

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override;

    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(
        oboe::AudioStream *audioStream,
        oboe::Result error
    ) override;

private:
    void closeStreamInternal();

    std::shared_ptr<oboe::AudioStream> mStream;
    std::unique_ptr<LockFreeRingBuffer> mRingBuffer;
    std::mutex mStreamMutex;

    int32_t mSampleRate{44100};
    int32_t mChannelCount{2};
    int32_t mBitDepth{16};
    size_t mBytesPerFrame{4};

    std::atomic<int64_t> mTotalFramesWritten{0};
    std::atomic<int64_t> mTotalFramesRendered{0};
    std::atomic<uint32_t> mUnderflowCount{0};

    DisconnectCallback mDisconnectCallback{nullptr};
    oboe::Result mLastOpenResult{oboe::Result::OK};
    oboe::Result mExclusiveOpenResult{oboe::Result::OK};
};
