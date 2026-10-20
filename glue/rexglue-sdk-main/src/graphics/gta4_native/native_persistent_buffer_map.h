#pragma once

#include <cstddef>
#include <cstdint>
#include <deque>
#include <vector>

namespace rex::graphics::gta4_native {

// Persistent buffer entries by key, for the recorder's per-draw lookups.
//
// Entries live in a deque, so their addresses never change while they exist
// (the owner retirement queue keeps pointers to them); freed slots are reused.
// The index is open addressing over 8-byte slots (entry number + 16 bits of
// the hash): a lookup touches one dense cache line and then the entry itself,
// where the node-based std::unordered_map cost two or three cache misses.
// Deletion shifts the following cluster back, so no tombstones accumulate.
template <typename Key, typename Entry, typename Hash>
class NativePersistentBufferMap {
 public:
  Entry* Find(const Key& key) {
    if (slots_.empty()) return nullptr;
    const size_t hash = hasher_(key);
    const uint32_t tag = Tag(hash);
    const size_t mask = slots_.size() - 1;
    for (size_t index = hash & mask;; index = (index + 1) & mask) {
      const Slot slot = slots_[index];
      if (!slot.entry) return nullptr;
      if (slot.tag == tag) {
        Entry& entry = entries_[slot.entry - 1];
        if (entry.key == key) return &entry;
      }
    }
  }

  bool Contains(const Key& key) { return Find(key) != nullptr; }

  // The key must not be present.
  Entry* Insert(const Entry& value) {
    if ((size_ + 1) * 2 > slots_.size()) Grow();
    uint32_t number;
    if (!free_.empty()) {
      number = free_.back();
      free_.pop_back();
      entries_[number - 1] = value;
    } else {
      entries_.push_back(value);
      number = uint32_t(entries_.size());
    }
    Place(number, hasher_(value.key));
    ++size_;
    return &entries_[number - 1];
  }

  void Erase(const Entry* entry) {
    if (!entry || slots_.empty()) return;
    const size_t hash = hasher_(entry->key);
    const size_t mask = slots_.size() - 1;
    size_t index = hash & mask;
    for (;; index = (index + 1) & mask) {
      const Slot slot = slots_[index];
      if (!slot.entry) return;  // not present
      if (&entries_[slot.entry - 1] == entry) break;
    }
    const uint32_t number = slots_[index].entry;
    // Backward-shift deletion: move later members of the cluster into the
    // hole when their home slot does not lie between the hole and them.
    for (size_t next = (index + 1) & mask;; next = (next + 1) & mask) {
      const Slot moving = slots_[next];
      if (!moving.entry) break;
      const size_t home = hasher_(entries_[moving.entry - 1].key) & mask;
      const bool stays = index <= next ? (index < home && home <= next)
                                       : (index < home || home <= next);
      if (stays) continue;
      slots_[index] = moving;
      index = next;
    }
    slots_[index] = Slot{};
    entries_[number - 1] = Entry{};
    free_.push_back(number);
    --size_;
  }

  void Clear() {
    slots_.clear();
    entries_.clear();
    free_.clear();
    size_ = 0;
  }

  size_t size() const { return size_; }

 private:
  struct Slot {
    uint32_t entry = 0;  // entry number + 1; 0 = empty
    uint32_t tag = 0;
  };

  static uint32_t Tag(size_t hash) { return uint32_t(uint64_t(hash) >> 48) | 1u; }

  void Place(uint32_t number, size_t hash) {
    const size_t mask = slots_.size() - 1;
    size_t index = hash & mask;
    while (slots_[index].entry) index = (index + 1) & mask;
    slots_[index] = Slot{number, Tag(hash)};
  }

  void Grow() {
    const size_t capacity = slots_.empty() ? 1024 : slots_.size() * 2;
    std::vector<Slot> previous = std::move(slots_);
    slots_.assign(capacity, Slot{});
    for (const Slot& slot : previous) {
      if (slot.entry) Place(slot.entry, hasher_(entries_[slot.entry - 1].key));
    }
  }

  std::vector<Slot> slots_;
  std::deque<Entry> entries_;
  std::vector<uint32_t> free_;
  size_t size_ = 0;
  Hash hasher_{};
};

}  // namespace rex::graphics::gta4_native
