import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import preflight
import build_layer


class PackagingChecks(unittest.TestCase):
    def test_unknown_base_is_rejected_before_inspection(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"unknown.apk"
            path.write_bytes(b"other APK")
            with self.assertRaisesRegex(ValueError,"SHA-256"):
                preflight.validate_base(path)

    def test_manifest_preserves_stock_game_and_launcher_and_isolates_session_process(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"AndroidManifest.xml"
            path.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.libertyrecomp.gtaiv"><application android:theme="@style/AppTheme.Fullscreen"><activity android:name="com.libertyrecomp.LauncherActivity" android:exported="true"/><activity android:name="com.libertyrecomp.LibertyActivity" android:process=":game"/></application></manifest>''')
            build_layer.manifest(path)
            root=ET.parse(path).getroot();app=root.find("application");ns=build_layer.ANDROID
            self.assertEqual(preflight.APP_ID,root.get("package"))
            self.assertEqual(preflight.APPLICATION,app.get(ns+"name"))
            activities={a.get(ns+"name"):a for a in app.findall("activity")}
            self.assertIn("com.libertyrecomp.LibertyActivity",activities)
            self.assertIn("com.libertyrecomp.LauncherActivity",activities)
            session=activities["com.xhyn.stock.SessionActivity"]
            self.assertEqual(":game",session.get(ns+"process"))
            self.assertEqual("false",session.get(ns+"exported"))
            self.assertIsNone(activities["com.xhyn.stock.PanelActivity"].get(ns+"launchMode"),"Tool-to-editor navigation needs a new Activity and a working back stack")
            self.assertNotIn("Translucent",session.get(ns+"theme"))
            provider=app.find("provider")
            self.assertEqual("false",provider.get(ns+"exported"))
            self.assertEqual("true",provider.get(ns+"grantUriPermissions"))

    def test_unexpected_application_is_not_silently_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"AndroidManifest.xml"
            path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name="ExistingApp"/></manifest>')
            with self.assertRaisesRegex(AssertionError,"Unexpected stock Application"):
                build_layer.manifest(path)

    def archives(self,folder,changed=None,missing=None,extra=None):
        base=Path(folder)/"base.apk";candidate=Path(folder)/"candidate.apk"
        entries={"AndroidManifest.xml":b"manifest", "classes.dex":b"stock dex", "assets/Resources/icon.png":b"stock asset", "lib/arm64-v8a/libmain.so":b"stock native", "META-INF/CERT.RSA":b"stock signature"}
        with zipfile.ZipFile(base,"w") as z:
            for key,value in entries.items():z.writestr(key,value)
        with zipfile.ZipFile(candidate,"w") as z:
            for key,value in entries.items():
                if key==missing or preflight.signature_entry(key):continue
                z.writestr(key,b"mutated" if key==changed else value)
            z.writestr("classes2.dex",b"new dex")
            if extra:z.writestr(extra,b"extra")
        return base,candidate

    def test_native_and_asset_mutations_and_missing_stock_dex_are_rejected(self):
        for changed,missing in (("lib/arm64-v8a/libmain.so",None),("assets/Resources/icon.png",None),(None,"classes.dex")):
            with self.subTest(changed=changed,missing=missing),tempfile.TemporaryDirectory() as folder:
                base,candidate=self.archives(folder,changed,missing)
                with patch.object(preflight,"validate_base",return_value={}):
                    with self.assertRaisesRegex(ValueError,"Stock payload changed"):
                        preflight.verify_candidate(base,candidate)

    def test_unexpected_native_library_addition_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            base,candidate=self.archives(folder,extra="lib/arm64-v8a/libpatched.so")
            with patch.object(preflight,"validate_base",return_value={}):
                with self.assertRaisesRegex(ValueError,"Unexpected package entries"):
                    preflight.verify_candidate(base,candidate)

    def test_duplicate_candidate_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            base,candidate=self.archives(folder)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore",UserWarning)
                with zipfile.ZipFile(candidate,"a") as z:z.writestr("classes.dex",b"duplicate")
            with patch.object(preflight,"validate_base",return_value={}):
                with self.assertRaisesRegex(ValueError,"Duplicate"):
                    preflight.verify_candidate(base,candidate)

    def test_signature_filter_keeps_stock_metadata(self):
        self.assertTrue(preflight.signature_entry("META-INF/CERT.RSA"))
        self.assertTrue(preflight.signature_entry("META-INF/MANIFEST.MF"))
        self.assertFalse(preflight.signature_entry("META-INF/version-control-info.textproto"))
        self.assertFalse(preflight.signature_entry("assets/NOTICE.SF"))

    def test_leb_decoding_rejects_non_terminating_value(self):
        self.assertEqual((129,2),preflight.uleb(b"\x81\x01",0))
        with self.assertRaisesRegex(ValueError,"LEB128"):
            preflight.uleb(b"\x80"*5,0)


if __name__=="__main__":unittest.main()
