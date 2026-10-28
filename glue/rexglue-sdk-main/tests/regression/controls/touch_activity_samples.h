#pragma once
#include <cstdint>

// Fixed physical gesture samples shared by pointer, transport and activity
// regressions. The transport fixture moves y=360 -> 440 -> 280. At 50 ms
// per move and radius 100, the theoretical axes +256/-512 saturate to the
// console's signed 8-bit limits +127/-128. Integration drags also use a smaller
// 30 px movement to exercise unsaturated input inside the activity surface.
namespace activity_samples {
inline constexpr uint64_t kBeginNs = 5'000'000'000;
inline constexpr uint64_t kMoveNsDelta = 50'000'000;
inline constexpr uint64_t kReverseNsDelta = 100'000'000;
inline constexpr uint64_t kMoveNs = kBeginNs + kMoveNsDelta;
inline constexpr uint64_t kReverseNs = kBeginNs + kReverseNsDelta;
inline constexpr uint64_t kExpiredNs = kBeginNs + 1'000'000'000;
inline constexpr uint64_t kCrossfadeHalfNs = 75'000'000;
inline constexpr uint64_t kCrossfadeDoneNs = 151'000'000;
inline constexpr float kRadius = 100.0f;
inline constexpr float kTravel = 80.0f;
inline constexpr float kMovePixels = 30.0f;
inline constexpr double kFrame = 1.0 / 60.0;
inline constexpr int32_t kStrokeForward = 127;
inline constexpr int32_t kStrokeBackward = -128;
}  // namespace activity_samples
