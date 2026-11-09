"""Regression checks for the focused base-UI patch and strict translation boundary."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build_base_ui as base


class BaseTranslationTest(unittest.TestCase):
    def test_catalog_contains_only_complete_english_translations(self):
        catalog = json.loads((ROOT / 'base-ui/english.json').read_text())
        self.assertEqual(299, len(catalog))
        for original, english in catalog.items():
            self.assertRegex(original, base.CYRILLIC)
            self.assertNotRegex(english, base.CYRILLIC)
            # These fragments concatenate numbers/paths; keep their separators.
            if original.startswith(' '): self.assertTrue(english.startswith(' '), original)
            if original.endswith(' '): self.assertTrue(english.endswith(' '), original)
            if original.startswith('\n'): self.assertTrue(english.startswith('\n'), original)
            if original.endswith('\n'): self.assertTrue(english.endswith('\n'), original)

    def test_smali_escapes_do_not_change_backslashes_or_apostrophes(self):
        self.assertEqual("This device's GPU (", base.decode_literal(r'"This device\'s GPU ("'))
        self.assertEqual("a\\'b", base.decode_literal(r'"a\\\'b"'))
        self.assertEqual('Я\n', base.decode_literal(r'"\u042f\n"'))

    def test_language_arg_patch_keeps_every_other_byte(self):
        for ending in (b'\n', b'\r\n'):
            source = ending.join([b'--renderer=vulkan', b'--user_language=4', b'--foo=4', b''])
            self.assertEqual(source.replace(b'user_language=4', b'user_language=0'), base.english_args(source))
        for invalid in (b'--user_language=0\n', b'--user_language=4\n--user_language=4\n'):
            with self.assertRaises(ValueError): base.english_args(invalid)

    def test_native_translation_only_overwrites_checked_literal_spans(self):
        source = bytearray(b'\xa5' * (0x4066b8 + 256))
        for offset, old, new in base.HINTS:
            source[offset:offset+len(old.encode())+1] = old.encode() + b'\0'
        result = base.english_hints(bytes(source))
        self.assertEqual(len(source), len(result))
        expected = bytearray(source)
        for offset, old, new in base.HINTS:
            expected[offset:offset+len(old.encode())] = new.encode().ljust(len(old.encode()),b'\0')
        self.assertEqual(bytes(expected), result)
        source[base.HINTS[0][0]] ^= 1
        with self.assertRaises(ValueError): base.english_hints(bytes(source))

    def test_unrecognized_translation_switch_keys_stop_the_build(self):
        # A translated switch key cannot retain its Russian Java hash.
        old='Графика'
        source=f'''.method public test()V
invoke-virtual {{p0}}, Ljava/lang/String;->hashCode()I
const-string v0, {json.dumps(old)}
.sparse-switch
    {base.java_hash(old):#x} -> :case
.end sparse-switch
.end method
'''
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); (root/'smali').mkdir(); (root/'res').mkdir()
            (root/'smali/test.smali').write_text(source)
            with self.assertRaisesRegex(ValueError,'hash-switch key'): base.translate_tree(root)


if __name__ == '__main__':
    unittest.main()
