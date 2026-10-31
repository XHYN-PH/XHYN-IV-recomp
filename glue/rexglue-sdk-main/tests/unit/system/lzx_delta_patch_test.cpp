#include <array>
#include <cstdint>
#include <span>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "../../../src/system/lzx_delta_patch.h"

namespace {

using Bytes = std::vector<uint8_t>;

void Record(Bytes& bytes, uint32_t source, uint32_t target, uint16_t length, uint16_t compressed) {
  for (uint32_t value : {source, target}) {
    for (int shift = 24; shift >= 0; shift -= 8) bytes.push_back(uint8_t(value >> shift));
  }
  for (uint16_t value : {length, compressed}) {
    bytes.push_back(uint8_t(value >> 8));
    bytes.push_back(uint8_t(value));
  }
}

int UnexpectedDecode(std::span<const uint8_t>, uint8_t*, size_t, uint8_t*, size_t) {
  FAIL("Fill/copy or rejected records must not enter the LZX decoder");
  return 99;
}

int Apply(std::span<const uint8_t> patch, std::span<uint8_t> image) {
  return rex::lzx::detail::ApplyDeltaPatch(patch, image.data(), image.size(), UnexpectedDecode);
}

}  // namespace

TEST_CASE("XEXP fill and copy records can begin at an unaligned byte", "[system][lzx-delta]") {
  Bytes records = {0xA5};
  Record(records, 0, 1, 2, 0);
  Record(records, 0, 4, 4, 1);
  std::array<uint8_t, 10> image = {0xC0, 1, 2, 3, 4, 5, 6, 7, 8, 0xD0};
  REQUIRE(Apply(std::span(records).subspan(1), std::span(image).subspan(1, 8)) == 0);
  const std::array<uint8_t, 10> expected = {0xC0, 1, 0, 0, 4, 1, 0, 0, 4, 0xD0};
  CHECK(image == expected);
}

TEST_CASE("XEXP copy records preserve overlapping source bytes", "[system][lzx-delta]") {
  Bytes records;
  Record(records, 0, 1, 4, 1);
  std::array<uint8_t, 6> image = {1, 2, 3, 4, 5, 6};
  REQUIRE(Apply(records, image) == 0);
  const std::array<uint8_t, 6> expected = {1, 1, 2, 3, 4, 6};
  CHECK(image == expected);
}

TEST_CASE("XEXP decoder rejects incomplete nonzero record headers", "[system][lzx-delta]") {
  const std::array<uint8_t, 12> header = {1};
  std::array<uint8_t, 4> image = {1, 2, 3, 4};
  const auto original = image;
  for (size_t length = 1; length < header.size(); ++length) {
    CHECK(Apply(std::span(header).first(length), image) != 0);
    CHECK(image == original);
  }
}

TEST_CASE("XEXP decoder accepts zero padding without reading a record", "[system][lzx-delta]") {
  std::array<uint8_t, 4> image = {1, 2, 3, 4};
  for (size_t length = 0; length < 12; ++length) {
    Bytes records;
    Record(records, 0, 1, 1, 0);
    records.resize(records.size() + length, 0);
    REQUIRE(Apply(records, image) == 0);
    CHECK(image[1] == 0);
  }
}

TEST_CASE("XEXP decoder bounds both source and destination ranges", "[system][lzx-delta]") {
  for (const auto [source, target, length, compressed] :
       {std::array<uint32_t, 4>{0, 4, 1, 0}, {0, 3, 2, 0}, {4, 0, 1, 1},
        {3, 0, 2, 1}, {0xFFFFFFFF, 0, 2, 1}, {0, 0xFFFFFFFF, 2, 0}}) {
    Bytes records;
    Record(records, source, target, uint16_t(length), uint16_t(compressed));
    std::array<uint8_t, 4> image = {1, 2, 3, 4};
    const auto original = image;
    CHECK(Apply(records, image) != 0);
    CHECK(image == original);
  }
}

TEST_CASE("XEXP compressed payload must fit inside its block", "[system][lzx-delta]") {
  Bytes records;
  Record(records, 0, 0, 1, 3);
  records.insert(records.end(), {0xAA, 0xBB});
  std::array<uint8_t, 4> image = {1, 2, 3, 4};
  CHECK(Apply(records, image) != 0);
  CHECK(image[0] == 1);
}

TEST_CASE("XEXP compressed record lengths do not round the next record", "[system][lzx-delta]") {
  Bytes records;
  Record(records, 2, 7, 2, 3);
  records.insert(records.end(), {9, 8, 7});
  Record(records, 0, 0, 1, 0);
  std::array<uint8_t, 10> image = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
  unsigned calls = 0;
  REQUIRE(rex::lzx::detail::ApplyDeltaPatch(records, image.data(), image.size(),
      [&](std::span<const uint8_t> input, uint8_t* output, size_t output_size,
          uint8_t* reference, size_t reference_size) {
        ++calls;
        CHECK(input.size() == 3);
        CHECK(input[0] == 9);
        CHECK(input[2] == 7);
        CHECK(output_size == 2);
        CHECK(reference_size == 2);
        CHECK(reference == image.data() + 2);
        output[0] = input[0];
        output[1] = reference[1];
        return 0;
      }) == 0);
  CHECK(calls == 1);
  const std::array<uint8_t, 10> expected = {0, 2, 3, 4, 5, 6, 7, 9, 4, 10};
  CHECK(image == expected);
}

TEST_CASE("XEXP decoder errors stop the following records", "[system][lzx-delta]") {
  Bytes records;
  Record(records, 0, 0, 1, 2);
  records.insert(records.end(), {1, 2});
  Record(records, 0, 1, 1, 0);
  std::array<uint8_t, 3> image = {1, 2, 3};
  CHECK(rex::lzx::detail::ApplyDeltaPatch(records, image.data(), image.size(),
      [](std::span<const uint8_t>, uint8_t*, size_t, uint8_t*, size_t) { return 7; }) == 7);
  CHECK(image[1] == 2);
}

TEST_CASE("XEXP terminators and empty patches require no destination", "[system][lzx-delta]") {
  Bytes records(12, 0);
  records.push_back(0xFF);
  CHECK(Apply(records, {}) == 0);
  CHECK(Apply({}, {}) == 0);
  records.clear();
  Record(records, 0, 0, 1, 0);
  CHECK(Apply(records, {}) != 0);
}
