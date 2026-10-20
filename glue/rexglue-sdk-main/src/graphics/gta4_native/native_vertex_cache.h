#pragma once
// Triangle-list reordering for the GPU's post-transform vertex cache (Tom
// Forsyth, "Linear-Speed Vertex Cache Optimisation"). GTA IV's index buffers
// are laid out for Xenos; on the Adreno 650 they reused only ~45% of vertex
// shader results (1.9M invocations for 1.2M triangles at the bridge). Only the
// order of whole triangles changes, and each triangle keeps its vertex order
// (winding), so a draw whose result does not depend on triangle order renders
// the same image.
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <vector>

namespace rex::graphics::gta4_native {

namespace vertex_cache_detail {
constexpr int kCacheSize = 32;
constexpr int kMaxValence = 64;

inline const std::array<float, kCacheSize>& CacheScores() {
  static const std::array<float, kCacheSize> scores = [] {
    std::array<float, kCacheSize> s{};
    for (int i = 0; i < kCacheSize; ++i) {
      // The three most recent vertices belong to the last triangle: a fixed
      // score so the next triangle does not simply reuse all three.
      s[i] = i < 3 ? 0.75f
                   : std::pow(1.0f - float(i - 3) / float(kCacheSize - 3), 1.5f);
    }
    return s;
  }();
  return scores;
}

inline const std::array<float, kMaxValence + 1>& ValenceScores() {
  static const std::array<float, kMaxValence + 1> scores = [] {
    std::array<float, kMaxValence + 1> s{};
    for (int i = 1; i <= kMaxValence; ++i) s[i] = 2.0f * std::pow(float(i), -0.5f);
    return s;
  }();
  return scores;
}

inline float VertexScore(int cache_position, uint32_t remaining) {
  if (!remaining) return -1.0f;
  float score = cache_position >= 0 ? CacheScores()[cache_position] : 0.0f;
  score += ValenceScores()[std::min<uint32_t>(remaining, kMaxValence)];
  return score;
}
}  // namespace vertex_cache_detail

// Reorders `triangle_count` triangles of `indices` into `out` (may not alias).
// Returns false (out untouched) when the range is too sparse to index densely.
template <typename Index>
bool OptimizeTriangleListForVertexCache(const Index* indices, uint32_t triangle_count,
                                        Index* out) {
  using namespace vertex_cache_detail;
  const uint32_t index_count = triangle_count * 3;
  if (!triangle_count) return false;
  Index minimum = indices[0], maximum = indices[0];
  for (uint32_t i = 1; i < index_count; ++i) {
    minimum = std::min(minimum, indices[i]);
    maximum = std::max(maximum, indices[i]);
  }
  const uint32_t vertex_count = uint32_t(maximum - minimum) + 1;
  if (vertex_count > index_count * 4 + 1024) return false;

  std::vector<uint32_t> triangle_offsets(vertex_count + 1, 0);  // CSR adjacency
  for (uint32_t i = 0; i < index_count; ++i) ++triangle_offsets[indices[i] - minimum + 1];
  for (uint32_t v = 0; v < vertex_count; ++v) triangle_offsets[v + 1] += triangle_offsets[v];
  std::vector<uint32_t> adjacency(index_count);
  {
    std::vector<uint32_t> fill(triangle_offsets.begin(), triangle_offsets.end() - 1);
    for (uint32_t i = 0; i < index_count; ++i) adjacency[fill[indices[i] - minimum]++] = i / 3;
  }
  std::vector<uint32_t> remaining(vertex_count);
  for (uint32_t v = 0; v < vertex_count; ++v)
    remaining[v] = triangle_offsets[v + 1] - triangle_offsets[v];
  std::vector<int8_t> cache_position(vertex_count, -1);
  std::vector<float> vertex_score(vertex_count);
  for (uint32_t v = 0; v < vertex_count; ++v) vertex_score[v] = VertexScore(-1, remaining[v]);
  std::vector<float> triangle_score(triangle_count);
  std::vector<uint8_t> emitted(triangle_count, 0);
  for (uint32_t t = 0; t < triangle_count; ++t) {
    triangle_score[t] = vertex_score[indices[t * 3] - minimum] +
                        vertex_score[indices[t * 3 + 1] - minimum] +
                        vertex_score[indices[t * 3 + 2] - minimum];
  }

  std::array<uint32_t, kCacheSize + 3> cache{};
  uint32_t cache_count = 0;
  uint32_t scan = 0;  // First possibly unemitted triangle for the fallback search.
  uint32_t best = 0;
  float best_score = -1e30f;
  for (uint32_t t = 0; t < triangle_count; ++t) {
    if (triangle_score[t] > best_score) best_score = triangle_score[t], best = t;
  }
  for (uint32_t written = 0; written < triangle_count; ++written) {
    const uint32_t t = best;
    emitted[t] = 1;
    std::array<uint32_t, 3> tri;
    for (int k = 0; k < 3; ++k) {
      out[written * 3 + k] = indices[t * 3 + k];
      tri[k] = indices[t * 3 + k] - minimum;
    }
    // Remove the triangle from its vertices' live lists.
    for (uint32_t v : tri) {
      uint32_t* begin = adjacency.data() + triangle_offsets[v];
      uint32_t* end = begin + remaining[v];
      uint32_t* found = std::find(begin, end, t);
      if (found != end) {
        *found = *(end - 1);
        --remaining[v];
      }
    }
    // Move its vertices to the front of the LRU cache.
    std::array<uint32_t, kCacheSize + 3> next{};
    uint32_t next_count = 0;
    for (uint32_t v : tri) {
      bool present = false;
      for (uint32_t i = 0; i < next_count; ++i) present |= next[i] == v;
      if (!present) next[next_count++] = v;
    }
    for (uint32_t i = 0; i < cache_count; ++i) {
      const uint32_t v = cache[i];
      if (v != tri[0] && v != tri[1] && v != tri[2]) next[next_count++] = v;
    }
    for (uint32_t i = 0; i < next_count; ++i) {
      cache_position[next[i]] = i < uint32_t(kCacheSize) ? int8_t(i) : int8_t(-1);
    }
    cache_count = std::min<uint32_t>(next_count, kCacheSize);
    std::copy(next.begin(), next.begin() + cache_count, cache.begin());
    // Rescore cached vertices (and the evicted tail) and their triangles,
    // choosing the next triangle among them.
    best_score = -1e30f;
    bool found_best = false;
    for (uint32_t i = 0; i < next_count; ++i) {
      const uint32_t v = next[i];
      const float updated = VertexScore(cache_position[v], remaining[v]);
      const float delta = updated - vertex_score[v];
      vertex_score[v] = updated;
      const uint32_t* list = adjacency.data() + triangle_offsets[v];
      for (uint32_t j = 0; j < remaining[v]; ++j) {
        const uint32_t adjacent = list[j];
        triangle_score[adjacent] += delta;
        if (triangle_score[adjacent] > best_score) {
          best_score = triangle_score[adjacent];
          best = adjacent;
          found_best = true;
        }
      }
    }
    if (!found_best && written + 1 < triangle_count) {
      // No triangle touches the cache: take the best remaining one in order.
      while (scan < triangle_count && emitted[scan]) ++scan;
      best = scan;
      best_score = triangle_score[scan];
      for (uint32_t u = scan + 1; u < triangle_count && u < scan + 256; ++u) {
        if (!emitted[u] && triangle_score[u] > best_score) best_score = triangle_score[u], best = u;
      }
    }
  }
  return true;
}

// One reordered index range. The recorder reads `indices` only after seeing
// state kReady (acquire); the optimizer thread writes them before (release).
struct NativeVertexCacheRange {
  enum State : int { kPending, kReady, kUnusable };
  std::atomic<int> state{kPending};
  std::vector<uint8_t> indices;
  // Recorder only: the persistent GPU copy of indices and the persistent
  // buffer epoch it was found in (the renderer's NativePersistentBufferEntry).
  void* persistent_entry = nullptr;
  uint64_t persistent_epoch = 0;
};

// Reorders ranges on a background thread so the recorder - the renderer's
// slowest stage - never stalls on a newly streamed mesh: draws use the title's
// order until the reordered copy is ready, typically a frame or two later.
class NativeVertexCacheOptimizer {
 public:
  static NativeVertexCacheOptimizer& Get() {
    static auto* optimizer = new NativeVertexCacheOptimizer();  // Never destroyed.
    return *optimizer;
  }

  // Copies the indices; false (nothing queued) when the queue is full.
  bool Enqueue(std::shared_ptr<NativeVertexCacheRange> range, const void* indices,
               uint32_t index_count, bool index32) {
    Job job{std::move(range), {}, index_count, index32};
    job.source.resize(size_t(index_count) * (index32 ? 4 : 2));
    std::memcpy(job.source.data(), indices, job.source.size());
    {
      std::lock_guard lock(mutex_);
      if (jobs_.size() >= kMaxPending) return false;
      jobs_.push_back(std::move(job));
    }
    condition_.notify_one();
    return true;
  }

 private:
  static constexpr size_t kMaxPending = 512;
  struct Job {
    std::shared_ptr<NativeVertexCacheRange> range;
    std::vector<uint8_t> source;
    uint32_t index_count;
    bool index32;
  };

  NativeVertexCacheOptimizer() { std::thread([this] { Run(); }).detach(); }

  void Run() {
    for (;;) {
      Job job;
      {
        std::unique_lock lock(mutex_);
        condition_.wait(lock, [this] { return !jobs_.empty(); });
        job = std::move(jobs_.front());
        jobs_.pop_front();
      }
      job.range->indices.resize(job.source.size());
      const uint32_t triangles = job.index_count / 3;
      const bool ok =
          job.index32
              ? OptimizeTriangleListForVertexCache(
                    reinterpret_cast<const uint32_t*>(job.source.data()), triangles,
                    reinterpret_cast<uint32_t*>(job.range->indices.data()))
              : OptimizeTriangleListForVertexCache(
                    reinterpret_cast<const uint16_t*>(job.source.data()), triangles,
                    reinterpret_cast<uint16_t*>(job.range->indices.data()));
      if (!ok) job.range->indices = {};
      job.range->state.store(ok ? NativeVertexCacheRange::kReady : NativeVertexCacheRange::kUnusable,
                             std::memory_order_release);
    }
  }

  std::mutex mutex_;
  std::condition_variable condition_;
  std::deque<Job> jobs_;
};

// Post-transform cache misses for a FIFO cache of `cache_size` (diagnostics).
template <typename Index>
uint32_t CountVertexCacheMisses(const Index* indices, uint32_t index_count, uint32_t cache_size) {
  std::vector<Index> fifo;
  uint32_t misses = 0;
  for (uint32_t i = 0; i < index_count; ++i) {
    if (std::find(fifo.begin(), fifo.end(), indices[i]) == fifo.end()) {
      ++misses;
      fifo.push_back(indices[i]);
      if (fifo.size() > cache_size) fifo.erase(fifo.begin());
    }
  }
  return misses;
}

}  // namespace rex::graphics::gta4_native
