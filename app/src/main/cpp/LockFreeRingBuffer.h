#pragma once

#include <atomic>
#include <vector>
#include <cstdint>
#include <cstring>
#include <algorithm>

/**
 * Cache-line aligned, lock-free Single Producer Single Consumer (SPSC) ring buffer.
 *
 * Designed specifically for high-throughput, zero-jitter PCM audio streaming:
 * - alignas(64) guarantees mReadIndex and mWriteIndex occupy different CPU cache lines,
 *   eliminating multi-core false sharing and cache-line bouncing.
 * - Single Producer (Media3 Decoder Thread) writes non-blocking buffers.
 * - Single Consumer (Oboe Real-Time Audio Callback) reads non-blocking frames on SCHED_FIFO thread.
 * - Zero mutexes, zero memory allocations during streaming.
 */
class LockFreeRingBuffer {
public:
    explicit LockFreeRingBuffer(size_t capacityBytes)
        : mCapacity(capacityBytes),
          mBuffer(capacityBytes, 0),
          mReadIndex(0),
          mWriteIndex(0) {}

    ~LockFreeRingBuffer() = default;

    // Disallow copy/move
    LockFreeRingBuffer(const LockFreeRingBuffer&) = delete;
    LockFreeRingBuffer& operator=(const LockFreeRingBuffer&) = delete;

    /**
     * Writes PCM bytes into the ring buffer.
     * Called strictly by the Media3 Decoder / JNI thread (Single Producer).
     */
    size_t write(const uint8_t* __restrict data, size_t bytesToWrite) noexcept {
        if (__builtin_expect(!data || bytesToWrite == 0, 0)) return 0;

        const size_t currentRead = mReadIndex.load(std::memory_order_acquire);
        const size_t currentWrite = mWriteIndex.load(std::memory_order_relaxed);
        const size_t availableSpace = mCapacity - (currentWrite - currentRead);
        const size_t toWrite = std::min(bytesToWrite, availableSpace);

        if (__builtin_expect(toWrite == 0, 0)) return 0;

        const size_t writePos = currentWrite % mCapacity;
        const size_t firstChunk = std::min(toWrite, mCapacity - writePos);
        std::memcpy(&mBuffer[writePos], data, firstChunk);

        const size_t secondChunk = toWrite - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(&mBuffer[0], data + firstChunk, secondChunk);
        }

        mWriteIndex.store(currentWrite + toWrite, std::memory_order_release);
        return toWrite;
    }

    /**
     * Reads PCM bytes from the ring buffer into the destination buffer.
     * Called strictly by the Oboe audio callback thread (Single Consumer).
     */
    size_t read(uint8_t* __restrict destination, size_t bytesToRead) noexcept {
        if (__builtin_expect(!destination || bytesToRead == 0, 0)) return 0;

        const size_t currentWrite = mWriteIndex.load(std::memory_order_acquire);
        const size_t currentRead = mReadIndex.load(std::memory_order_relaxed);
        const size_t availableData = currentWrite - currentRead;
        const size_t toRead = std::min(bytesToRead, availableData);

        if (__builtin_expect(toRead == 0, 0)) return 0;

        const size_t readPos = currentRead % mCapacity;
        const size_t firstChunk = std::min(toRead, mCapacity - readPos);
        std::memcpy(destination, &mBuffer[readPos], firstChunk);

        const size_t secondChunk = toRead - firstChunk;
        if (secondChunk > 0) {
            std::memcpy(destination + firstChunk, &mBuffer[0], secondChunk);
        }

        mReadIndex.store(currentRead + toRead, std::memory_order_release);
        return toRead;
    }

    /**
     * Resets the buffer to empty state.
     * Should only be called when audio stream is stopped/paused or format changes.
     */
    void reset() noexcept {
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
    }

    size_t getAvailableRead() const noexcept {
        const size_t currentWrite = mWriteIndex.load(std::memory_order_acquire);
        const size_t currentRead = mReadIndex.load(std::memory_order_relaxed);
        return (currentWrite >= currentRead) ? (currentWrite - currentRead) : 0;
    }

    size_t getAvailableWrite() const noexcept {
        const size_t currentRead = mReadIndex.load(std::memory_order_acquire);
        const size_t currentWrite = mWriteIndex.load(std::memory_order_relaxed);
        const size_t used = currentWrite - currentRead;
        return (mCapacity > used) ? (mCapacity - used) : 0;
    }

    size_t capacity() const noexcept {
        return mCapacity;
    }

private:
    const size_t mCapacity;
    std::vector<uint8_t> mBuffer;

    // Cache-line isolation (64 bytes) prevents multi-core cache invalidation thrashing
    alignas(64) std::atomic<size_t> mReadIndex{0};
    alignas(64) std::atomic<size_t> mWriteIndex{0};
};
