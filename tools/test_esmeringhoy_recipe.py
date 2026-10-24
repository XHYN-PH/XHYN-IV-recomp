#!/usr/bin/env python3
"""Exercise source-drift rejection without downloading or compiling Mesa."""
from pathlib import Path
import hashlib
import importlib.util
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('recipe', ROOT / 'drivers/esmeringhoy/build.py')
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)


class RecipeTests(unittest.TestCase):
    def test_source_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def git(*args):
                return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.DEVNULL, text=True).strip()
            git('init', '-q')
            (root / 'source.c').write_text('base')
            (root / 'other.c').write_text('untouched')
            git('add', '.')
            git('-c', 'user.name=Recipe test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'Fixture')
            (root / 'source.c').write_text('patched')
            lock = {'mesa_commit': git('rev-parse', 'HEAD'),
                    'patched_files_sha256': {'source.c': hashlib.sha256(b'patched').hexdigest()}}
            recipe.verify_source(root, lock)
            (root / 'other.c').write_text('unexpected edit')
            with self.assertRaises(SystemExit): recipe.verify_source(root, lock)
            self.assertEqual((root / 'other.c').read_text(), 'unexpected edit')
            (root / 'other.c').write_text('untouched')
            (root / 'injected.c').write_text('unexpected file')
            with self.assertRaises(SystemExit): recipe.verify_source(root, lock)
            (root / 'injected.c').unlink()
            (root / 'source.c').write_text('different patch')
            with self.assertRaises(SystemExit): recipe.verify_source(root, lock)
            (root / 'source.c').unlink()
            (root / 'replacement').write_text('patched')
            (root / 'source.c').symlink_to(root / 'replacement')
            with self.assertRaises(SystemExit): recipe.verify_source(root, lock)


if __name__ == '__main__': unittest.main()
