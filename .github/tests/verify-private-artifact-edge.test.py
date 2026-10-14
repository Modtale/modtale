import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "verify-private-artifact-edge.py"
spec = importlib.util.spec_from_file_location("artifact_edge_verify", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ArtifactEdgeVerifyTest(unittest.TestCase):
    def test_rejects_any_public_route_to_private_bucket_or_managed_public_endpoint(self):
        def cloudflare(_account, bucket, route, _token):
            if route == "managed":
                return {"enabled": False}
            return {"domains": [{"domain": "cdn.modtale.net", "enabled": True}]}

        with patch.object(module, "cloudflare", side_effect=cloudflare):
            with self.assertRaisesRegex(ValueError, "Private artifact bucket"):
                module.domains_are_private("account", "public", "private", "cdn.modtale.net", "token")

        def managed_bypass(_account, bucket, route, _token):
            if route == "managed":
                return {"enabled": bucket == "public"}
            return {"domains": []}

        with patch.object(module, "cloudflare", side_effect=managed_bypass):
            with self.assertRaisesRegex(ValueError, "R2-managed"):
                module.domains_are_private("account", "public", "private", "cdn.modtale.net", "token")

    def test_requires_all_prefixes_denied_for_head_and_range_while_media_works(self):
        keys = ["files/mod/a.jar", "modpack-overrides/b.zip", "modpacks/c.zip"]

        def probe(_cdn, key, method, _headers=None):
            return 200 if key == "images/media.png" else 403

        with patch.object(module, "probe", side_effect=probe) as observed:
            module.verify_samples("cdn.modtale.net", keys, "images/media.png")
            self.assertEqual(7, observed.call_count)
        with patch.object(module, "probe", return_value=200):
            with self.assertRaisesRegex(ValueError, "HEAD"):
                module.verify_samples("cdn.modtale.net", keys, "images/media.png")
        with self.assertRaisesRegex(ValueError, "each protected prefix"):
            module.verify_samples("cdn.modtale.net", keys[:2], "images/media.png")


if __name__ == "__main__":
    unittest.main()
