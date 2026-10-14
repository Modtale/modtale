import importlib.util
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "copy-private-artifacts.py"
spec = importlib.util.spec_from_file_location("private_artifact_copy", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrivateArtifactCopyTest(unittest.TestCase):
    def test_inventory_paginates_all_artifact_prefixes_and_rejects_stalls(self):
        def aws(_endpoint, *args):
            prefix = args[args.index("--prefix") + 1]
            if prefix == "files/" and "--continuation-token" not in args:
                return {"Contents": [{"Key": "files/mod/a.jar", "Size": 3}],
                        "IsTruncated": True, "NextContinuationToken": "next"}
            if prefix == "files/":
                return {"Contents": [{"Key": "files/mod/b.jar", "Size": 4}], "IsTruncated": False}
            return {"Contents": [], "IsTruncated": False}

        with patch.object(module, "aws", side_effect=aws):
            self.assertEqual([("files/mod/a.jar", 3), ("files/mod/b.jar", 4)],
                             list(module.inventory("https://r2.test", "public")))
        with patch.object(module, "aws", return_value={"Contents": [], "IsTruncated": True}):
            with self.assertRaisesRegex(ValueError, "did not advance"):
                list(module.inventory("https://r2.test", "public"))

    def test_copy_verifies_destination_bytes_and_preserves_source(self):
        calls = []

        def aws(_endpoint, *args):
            calls.append(args)
            if args[0] == "head-object":
                return {}
            if args[0] == "get-object":
                bucket = args[args.index("--bucket") + 1]
                Path(args[-1]).write_bytes(b"abc" if bucket == "public" else b"bad")
                return {"ContentLength": 3}
            raise AssertionError(args)

        with tempfile.TemporaryDirectory() as directory, patch.object(module, "aws", side_effect=aws):
            with self.assertRaisesRegex(ValueError, "do not match"):
                module.copy_and_verify("https://r2.test", "public", "private", "files/mod/a.jar", 3,
                                       Path(directory))
        self.assertFalse(any(args[0] == "delete-object" for args in calls))

    def test_new_copy_preserves_bytes_and_sets_private_cache_policy(self):
        stored = {}
        calls = []

        def aws(_endpoint, *args):
            calls.append(args)
            if args[0] == "get-object":
                bucket = args[args.index("--bucket") + 1]
                content = b"good" if bucket == "public" else stored["bytes"]
                Path(args[-1]).write_bytes(content)
                return {"ContentLength": len(content), "ContentType": "application/java-archive"}
            if args[0] == "put-object":
                stored["bytes"] = Path(args[args.index("--body") + 1]).read_bytes()
                return {}
            raise AssertionError(args)

        with tempfile.TemporaryDirectory() as directory, patch.object(module, "aws", side_effect=aws), \
                patch.object(module, "destination_exists", return_value=False):
            existed, digest = module.copy_and_verify("https://r2.test", "public", "private",
                                                     "files/mod/a.jar", 4, Path(directory))
        self.assertFalse(existed)
        self.assertEqual(b"good", stored["bytes"])
        self.assertEqual(hashlib.sha256(b"good").hexdigest(), digest)
        self.assertTrue(any(args[0] == "put-object" and
                            args[args.index("--cache-control") + 1] == "private, no-store" for args in calls))


if __name__ == "__main__":
    unittest.main()
