#include <rex/ui/vulkan/address_int64_rewrite.h>
#include <fstream>
#include <iostream>
#include <iterator>
#include <cstring>
#ifdef XHYN_TEST_SMOLV
#include <smolv.h>
#endif

int main(int argc, char** argv) {
  if (argc != 3 && argc != 4) return 2;
  std::ifstream input(argv[1], std::ios::binary | std::ios::ate);
  if (!input) return 2;
  auto size = input.tellg();
  if (size < 20 || size > 16 * 1024 * 1024) return 2;
  std::vector<char> bytes(size_t(size), 0);
  input.seekg(0);
  if (!input.read(bytes.data(), size)) return 2;
  std::vector<uint32_t> code;
#ifdef XHYN_TEST_SMOLV
  if (argc == 4) {
    size_t decoded = smolv::GetDecodedBufferSize(bytes.data(), bytes.size());
    if (!decoded || decoded % 4 || decoded > 16 * 1024 * 1024) return 2;
    code.resize(decoded / 4);
    if (!smolv::Decode(bytes.data(), bytes.size(), code.data(), decoded)) return 2;
    if (!std::strcmp(argv[3], "decode")) {
      std::ofstream output(argv[2], std::ios::binary);
      output.write(reinterpret_cast<const char*>(code.data()), code.size() * 4);
      return output ? 0 : 2;
    }
  } else
#endif
  {
    if (size % 4) return 2;
    code.resize(size_t(size) / 4);
    std::memcpy(code.data(), bytes.data(), size_t(size));
  }
  std::string reason;
  auto rewritten = rex::ui::vulkan::util::RewriteAddressInt64(code, &reason);
  if (!rewritten) { std::cerr << reason << '\n'; return 1; }
  std::ofstream output(argv[2], std::ios::binary);
  output.write(reinterpret_cast<const char*>(rewritten->data()), rewritten->size() * 4);
  return output ? 0 : 2;
}
