#!/usr/bin/env python3
"""Failure-injection checks for the generated-core consistency gate."""
from pathlib import Path
import tempfile
import unittest

from audit_recomp_core import audit, SDK_RELATIVE


class RecompAuditTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.sdk = self.root / SDK_RELATIVE
        self.generated = self.sdk / "gta4-recomp/generated"
        self.generated.mkdir(parents=True)
        names = ["sub_82140000", "sub_82140004", "__imp__Fake", "rexcrt_memset"]
        self.write("gta4_init.h", "#define REX_CODE_BASE 0x82140000ull\n"
                   "#define REX_CODE_SIZE 0x1000ull\n" +
                   "\n".join(f"DECLARE_REX_FUNC({name})" for name in names))
        self.write("gta4_register.cpp", "\n".join(
            f"registrar->SetFunction(0x{0x82140000 + i * 4:X}, {name});"
            for i, name in enumerate(names)))
        self.write("gta4_init.cpp", "\n".join(
            f"{{ 0x{0x82140000 + i * 4:X}, {name} }}," for i, name in enumerate(names)))
        self.write("sources.cmake", "gta4_recomp.0.cpp\ngta4_recomp.1.cpp\n")
        self.write("gta4_recomp.0.cpp", "DEFINE_REX_FUNC(sub_82140000) {\n"
                   "sub_82140004(ctx, base);\n__imp__Fake(ctx, base);\n}")
        self.write("gta4_recomp.1.cpp", "DEFINE_REX_FUNC(sub_82140004) {\n"
                   "__builtin_trap(); // Switch case out of range\n}")
        self.kernel = self.sdk / "src/kernel/fake.cpp"
        self.kernel.parent.mkdir(parents=True)
        self.kernel.write_text("REX_EXPORT_STUB(__imp__Fake);\n")

    def write(self, name, content):
        (self.generated / name).write_text(content)

    def check_failure(self, fragment):
        report = audit(self.root)
        self.assertFalse(report["consistency_passed"])
        self.assertTrue(any(fragment in error for error in report["errors"]), report["errors"])

    def test_consistency_preserves_unfinished_inventory(self):
        report = audit(self.root)
        self.assertTrue(report["consistency_passed"], report["errors"])
        self.assertEqual(report["counts"]["generated_function_bodies"], 2)
        self.assertEqual(report["generic_stubs"][0]["generated_direct_calls"], 1)
        self.assertEqual(report["generated_traps_by_reason"], {"Switch case out of range": 1})
        self.assertNotIn("completion_percent", report)

    def test_duplicate_addresses_fail(self):
        path = self.generated / "gta4_register.cpp"
        path.write_text(path.read_text().replace("0x82140004,", "0x82140000,"))
        self.check_failure("Duplicate guest addresses")

    def test_mapping_drift_fails(self):
        path = self.generated / "gta4_init.cpp"
        path.write_text(path.read_text().replace("0x82140004,", "0x82140010,"))
        self.check_failure("PPCFuncMappings disagree")

    def test_missing_generated_body_fails(self):
        self.write("gta4_recomp.1.cpp", "// body lost during regeneration\n")
        self.check_failure("Missing generated bodies")

    def test_unregistered_direct_call_fails(self):
        with (self.generated / "gta4_recomp.0.cpp").open("a") as stream:
            stream.write("\nsub_82140010(ctx, base);\n")
        self.check_failure("direct-call targets")

    def test_omitted_translation_unit_fails(self):
        self.write("sources.cmake", "gta4_recomp.0.cpp\n")
        self.check_failure("source list differs")

    def test_missing_export_fails(self):
        self.kernel.unlink()
        self.check_failure("Unclassified kernel import")

    def test_missing_declaration_fails(self):
        path = self.generated / "gta4_init.h"
        path.write_text(path.read_text().replace("DECLARE_REX_FUNC(sub_82140004)", ""))
        self.check_failure("Declarations differ")

    def test_ordinary_wrapper_stub_diagnostics_are_retained(self):
        self.kernel.write_text('void Fake_entry() {\n  Warn("[STUB] missing");\n}\n'
                               'REX_EXPORT(__imp__Fake, fake::Fake_entry)\n')
        report = audit(self.root)
        self.assertTrue(report["consistency_passed"], report["errors"])
        self.assertEqual(report["generic_stubs"], [])
        self.assertEqual(report["imports_with_stub_or_partial_diagnostics"][0]["symbol"],
                         "__imp__Fake")

    def test_comments_do_not_mark_a_wrapper_unimplemented(self):
        self.kernel.write_text('void Fake_entry() {\n  // Previous STUB replaced.\n}\n'
                               'REX_EXPORT(__imp__Fake, fake::Fake_entry)\n')
        self.assertEqual(audit(self.root)["imports_with_stub_or_partial_diagnostics"], [])


if __name__ == "__main__":
    unittest.main()
