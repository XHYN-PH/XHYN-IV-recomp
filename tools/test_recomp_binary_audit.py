#!/usr/bin/env python3
"""Synthetic executable tests; contains no game code or assets."""
from pathlib import Path
import struct
import tempfile
import unittest

from recomp_binary_audit import audit_binary


BASE = 0x82140000


class BinaryAuditTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "synthetic.xex"
        header = bytearray(0x200)
        struct.pack_into(">4sIIIII", header, 0, b"XEX2", 1, 0x200, 0, 0, 2)
        struct.pack_into(">IIII", header, 24, 0x3FF, 0x80, 0x40006, 0x100)
        struct.pack_into(">IHH", header, 0x80, 8, 0, 0)
        struct.pack_into(">IIII", header, 0x100, 0, 0x805, 5, 0x545407F2)
        image = bytearray(0x3000)
        image[:2] = b"MZ"
        struct.pack_into("<I", image, 0x3C, 0x80)
        image[0x80:0x84] = b"PE\0\0"
        struct.pack_into("<HH", image, 0x84, 0x1F2, 2)
        struct.pack_into("<H", image, 0x94, 0xE0)
        optional = 0x98
        struct.pack_into("<H", image, optional, 0x10B)
        struct.pack_into("<I", image, optional + 28, BASE - 0x1000)
        struct.pack_into("<I", image, optional + 56, len(image))
        struct.pack_into("<I", image, optional + 92, 16)
        struct.pack_into("<II", image, optional + 96 + 24, 0x2000, 8)
        section = optional + 0xE0
        for name, size, rva, raw, flags in (
                (b".text", 16, 0x1000, 0x400, 0x60000020),
                (b".pdata", 8, 0x2000, 0x800, 0x40000040)):
            image[section:section + len(name)] = name
            struct.pack_into("<IIII", image, section + 8, size, rva, size, raw)
            struct.pack_into("<I", image, section + 36, flags)
            section += 40
        # The memory image uses RVA, while original file-offset locations contain
        # decoys. Confusing the layouts must fail the independent call-site check.
        struct.pack_into(">IIII", image, 0x1000, 0x4800000D, 0x4E800020, 0x4E800020, 0x4E800020)
        struct.pack_into(">I", image, 0x400, 0x48000005)
        struct.pack_into(">II", image, 0x2000, BASE, 1 << 8)
        self.data = header + image
        self.path.write_bytes(self.data)
        self.registered = {BASE, BASE + 4, BASE + 8, BASE + 12}

    def report(self, registered=None, calls=None):
        return audit_binary(self.path, self.registered if registered is None else registered,
                            {BASE: BASE + 12} if calls is None else calls)

    def test_memory_image_uses_virtual_addresses(self):
        result = self.report()
        self.assertTrue(result["consistency_passed"], result["errors"])
        self.assertEqual(result["pdata_entries"], 1)
        self.assertEqual(result["unique_in_code_bl_targets"], 1)
        self.assertEqual(result["generated_bl_sites_checked"], 1)

    def test_missing_pdata_binding_fails(self):
        result = self.report(registered=self.registered - {BASE})
        self.assertFalse(result["consistency_passed"])
        self.assertEqual(result["missing_pdata_bindings"], [f"0x{BASE:08X}"])

    def test_missing_direct_call_target_fails(self):
        result = self.report(registered=self.registered - {BASE + 12})
        self.assertFalse(result["consistency_passed"])
        self.assertEqual(result["missing_bl_bindings"], [f"0x{BASE + 12:08X}"])

    def test_generated_call_drift_fails(self):
        result = self.report(calls={BASE: BASE + 8})
        self.assertFalse(result["consistency_passed"])
        self.assertEqual(len(result["generated_bl_site_mismatches"]), 1)

    def test_truncated_metadata_fails(self):
        self.path.write_bytes(self.data[:0x200 + 0x2004])
        with self.assertRaisesRegex(ValueError, "exception DataDirectory"):
            self.report()

    def test_encrypted_input_requires_preparation(self):
        struct.pack_into(">H", self.data, 0x84, 1)
        self.path.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "decrypted, uncompressed"):
            self.report()

    def test_other_game_revision_fails(self):
        struct.pack_into(">I", self.data, 0x104, 5)
        self.path.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "TU8"):
            self.report()

    def test_oversized_optional_table_fails(self):
        struct.pack_into(">I", self.data, 20, 4097)
        self.path.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "optional-header table"):
            self.report()

    def test_exception_function_range_fails(self):
        struct.pack_into(">I", self.data, 0x200 + 0x2004, 100 << 8)
        self.path.write_bytes(self.data)
        with self.assertRaisesRegex(ValueError, "Exception entry"):
            self.report()

    def test_unannotated_exception_sites_retain_original_scope_metadata(self):
        image = 0x200
        struct.pack_into("<H", self.data, image + 0x86, 3)
        section = image + 0x98 + 0xE0 + 80
        self.data[section:section + 6] = b".rdata"
        struct.pack_into("<IIII", self.data, section + 8, 0x300, 0x500, 0x300, 0x500)
        struct.pack_into("<I", self.data, section + 36, 0x40000040)
        struct.pack_into(">II", self.data, image + 0xFF8, BASE + 8, BASE - 0x1000 + 0x600)
        struct.pack_into(">II", self.data, image + 0x2000, BASE, 0x80000100)
        struct.pack_into(">IIIII", self.data, image + 0x600, 1, BASE, BASE + 4, BASE + 8, BASE)
        self.path.write_bytes(self.data)
        result = self.report(calls={})
        self.assertTrue(result["consistency_passed"])
        self.assertEqual(result["unannotated_bl_candidate_count"], 1)
        context = result["unannotated_bl_candidates"][0]["exception_scope_context"]
        self.assertEqual(context["function"], f"0x{BASE:08X}")
        self.assertEqual(context["scopes"][0]["handler"], f"0x{BASE:08X}")


if __name__ == "__main__":
    unittest.main()
