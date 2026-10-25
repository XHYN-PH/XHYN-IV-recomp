#pragma once

#include <algorithm>
#include <condition_variable>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

namespace rex::graphics::gta4_native {

// A bounded compiler pool, including active and unconsumed results. Callers
// publish results on their owner thread. Stop joins the active driver call;
// shader modules, layouts, caches and the device must outlive this object.
template <typename Key, typename Result, typename Hash>
class NativePipelineCompiler {
 public:
  using Compile = std::function<Result()>;
  explicit NativePipelineCompiler(size_t capacity, std::function<void()> idle = {}, size_t workers = 1)
      : capacity_(std::max<size_t>(capacity, 1)), idle_(std::move(idle)) {
    try{for(size_t i=0;i<std::clamp<size_t>(workers,1,5);++i)threads_.emplace_back([this]{Run();});}
    catch(...){Stop();throw;}
  }
  ~NativePipelineCompiler() { Stop(); }
  NativePipelineCompiler(const NativePipelineCompiler&) = delete;
  NativePipelineCompiler& operator=(const NativePipelineCompiler&) = delete;

  bool Enqueue(const Key& key, Compile compile, bool demanded = false) {
    std::lock_guard lock(mutex_);
    if (stopping_) {
      return false;
    }
    if (const auto found = jobs_.find(key); found != jobs_.end()) {
      if (demanded) Promote(key, found->second);
      return true;
    }
    if (jobs_.size() >= capacity_) {
      if (!demanded || queue_.empty()) {
        return false;
      }
      // Evicting a demanded job would strand its waiter forever.
      const auto disposable = std::find_if(queue_.rbegin(), queue_.rend(), [&](const Key& queued) {
        return !jobs_.at(queued)->demanded;
      });
      if (disposable == queue_.rend()) return false;
      jobs_.erase(*disposable);
      queue_.erase(std::next(disposable).base());
    }
    auto job = std::make_shared<Job>();
    job->compile = std::move(compile);
    job->demanded = demanded;
    jobs_.emplace(key, job);
    if (demanded) {
      queue_.push_front(key);
    } else {
      queue_.push_back(key);
    }
    changed_.notify_all();
    return true;
  }

  // Demand promotes an existing speculative job, preserving exact deduplication.
  // No renderer state or Vulkan lifetime is changed while waiting.
  std::optional<Result> Take(const Key& key, bool wait) {
    std::unique_lock lock(mutex_);
    const auto found = jobs_.find(key);
    if (found == jobs_.end()) {
      return std::nullopt;
    }
    const auto job = found->second;
    if (wait && !job->result) {
      Promote(key, job);
      changed_.wait(lock, [&] { return job->result.has_value() || stopping_; });
    }
    if (!job->result) {
      return std::nullopt;
    }
    Result result = std::move(*job->result);
    completed_.erase(key);
    jobs_.erase(key);
    return result;
  }

  std::vector<std::pair<Key, Result>> TakeCompleted() {
    std::lock_guard lock(mutex_);
    std::vector<std::pair<Key, Result>> completed;
    completed.reserve(completed_.size());
    for (const auto& key : completed_) {
      auto it = jobs_.find(key);
      completed.emplace_back(it->first, std::move(*it->second->result));
      jobs_.erase(it);
    }
    completed_.clear();
    return completed;
  }

  bool Contains(const Key& key) const {
    std::lock_guard lock(mutex_);
    return jobs_.contains(key);
  }
  struct Progress { size_t queued=0, completed=0; bool active=false; size_t active_count=0; };
  Progress GetProgress() const {
    std::lock_guard lock(mutex_);
    return {queue_.size(),completed_.size(),active_!=0,active_};
  }
  void WaitUntilIdle() {
    std::unique_lock lock(mutex_);
    changed_.wait(lock, [this] { return !active_ && queue_.empty(); });
  }
  void Stop() {
    {
      std::lock_guard lock(mutex_);
      stopping_ = true;
      queue_.clear();
      changed_.notify_all();
    }
    for(auto& thread:threads_)if(thread.joinable())thread.join();
  }

 private:
  struct Job {
    Compile compile;
    std::optional<Result> result;
    bool demanded = false;
  };
  void Promote(const Key& key, const std::shared_ptr<Job>& job) {
    job->demanded = true;
    const auto queued = std::find(queue_.begin(), queue_.end(), key);
    if (queued != queue_.end() && queued != queue_.begin()) {
      queue_.erase(queued);
      queue_.push_front(key);
    }
    changed_.notify_all();
  }
  void Run() {
    std::unique_lock lock(mutex_);
    for (;;) {
      changed_.wait(lock, [this] { return stopping_ || !queue_.empty(); });
      if (stopping_) {
        return;
      }
      const Key key = queue_.front();
      queue_.pop_front();
      const auto job = jobs_.at(key);
      ++active_;
      lock.unlock();
      Result result{};
      try {
        result = job->compile();
      } catch (...) {
        // Allocation/cache I/O failure is an unsuccessful job, never process
        // termination on a background thread. The owner retains exact fallback.
      }
      lock.lock();
      job->result = std::move(result);
      completed_.insert(key);
      job->compile = {};
      --active_;
      changed_.notify_all();
      if (!stopping_ && queue_.empty() && !active_ && idle_) {
        ++active_;
        lock.unlock();
        try {
          idle_();
        } catch (...) {
        }
        lock.lock();
        --active_;
        changed_.notify_all();
      }
    }
  }

  const size_t capacity_;
  std::function<void()> idle_;
  mutable std::mutex mutex_;
  std::condition_variable changed_;
  bool stopping_ = false;
  size_t active_ = 0;
  std::deque<Key> queue_;
  std::unordered_map<Key, std::shared_ptr<Job>, Hash> jobs_;
  std::unordered_set<Key, Hash> completed_;
  std::vector<std::thread> threads_;
};

}  // namespace rex::graphics::gta4_native
