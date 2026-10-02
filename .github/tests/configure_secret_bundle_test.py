from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
from configure_secret_bundle import plan, restore_legacy_development
from secret_bundle import BundleError
class DeployPlanTests(unittest.TestCase):
    def test_off_is_exact_noop(self):self.assertEqual(plan('false','prod',{}),[])
    def test_prod_shared_and_original_hytale(self):
        args=plan('true','prod',{'shared':'4'})
        self.assertIn('/app/secrets/bundles/shared.json=MODTALE_CONFIG_SHARED:4',args)
        self.assertIn('HYTALE_CLIENT_SECRET=HYTALE_CLIENT_SECRET:latest',args)
        self.assertNotIn('HYTALE_CLIENT_SECRET',args[args.index('--remove-secrets')+1].split(','))
        self.assertFalse(any('CONFIG_PRODUCTION' in x for x in args))
        self.assertNotIn('--set-secrets',args)
    def test_warden_keeps_existing_rotation_path(self):
        args=plan('true','dev',{'shared':'1'})
        self.assertNotIn('WARDEN_API_KEY',args[args.index('--remove-secrets')+1].split(','))
        self.assertIn('WARDEN_API_KEY=WARDEN_API_KEY:latest',args)
        self.assertIn('HYTALE_CLIENT_SECRET',args[args.index('--remove-secrets')+1].split(','))
        self.assertFalse(any('HYTALE_CLIENT_SECRET=' in x for x in args))
    def test_dev_cannot_mount_prod(self):
        with self.assertRaises(BundleError):plan('true','dev',{'shared':'1','production':'1'})
        self.assertFalse(any('CONFIG_PRODUCTION' in x for x in plan('true','dev',{'shared':'1'})))
    def test_prod_rejects_removed_bundle_pin(self):
        with self.assertRaises(BundleError):plan('true','prod',{'shared':'1','production':'1'})
    def test_requires_numeric_pins(self):
        for pin in ('latest','','0'):
            with self.assertRaises(BundleError):plan('true','dev',{'shared':pin})
    def test_preview_required(self):
        for profile,boundary in [('branch-preview','branch-preview'),('pr-preview','pr-preview')]:
            with self.assertRaises(BundleError):plan('true',profile,{boundary:'1'})
    def test_preview_boundaries(self):
        branch=plan('true','branch-preview',{'branch-preview':'2'},'warden-v3')
        pr=plan('true','pr-preview',{'pr-preview':'4'},'123')
        self.assertTrue(any('CONFIG_BRANCH_PREVIEW:2' in x for x in branch))
        self.assertTrue(any('CONFIG_PR_PREVIEW:4' in x for x in pr))
        self.assertFalse(any('CONFIG_SHARED' in x for x in branch+pr))
        self.assertIn('WARDEN_API_KEY',branch[branch.index('--remove-secrets')+1].split(','))
        self.assertIn('WARDEN_API_KEY',pr[pr.index('--remove-secrets')+1].split(','))
    def test_invalid_flag(self):
        with self.assertRaises(BundleError):plan('yes','prod',{})
    def test_explicit_legacy_recovery_clears_bundle_mode(self):
        names=('MONGODB_URI R2_ACCESS_KEY R2_SECRET_KEY R2_ENDPOINT SMTP_HOST SMTP_USERNAME SMTP_PASSWORD '
               'PRE_AUTH_SECRET GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET '
               'GITLAB_CLIENT_ID GITLAB_CLIENT_SECRET DISCORD_CLIENT_ID DISCORD_CLIENT_SECRET TWITTER_CLIENT_ID '
               'TWITTER_CLIENT_SECRET BLUESKY_CLIENT_ID WARDEN_API_KEY WARDEN_URL WIKI_API_KEY '
               'DISCORD_WEBHOOK_URL ADMIN_DISCORD_WEBHOOK_URL').split()
        args=restore_legacy_development({name:'1' for name in names})
        self.assertIn('MODTALE_SECRET_BUNDLES_ENABLED=false',args)
        self.assertIn('WARDEN_API_KEY=WARDEN_API_KEY:latest',args)
        self.assertIn('WARDEN_URL=WARDEN_URL:1',args)
        self.assertFalse(any('HYTALE_CLIENT_SECRET' in x for x in args))
        self.assertNotIn('--set-secrets',args)
    def test_recovery_requires_all_legacy_sources(self):
        with self.assertRaises(BundleError):restore_legacy_development({})
    def test_reserved_branches_are_rejected(self):
        for slug in ('main','develop','dev'):
            with self.assertRaises(BundleError):plan('true','branch-preview',{'branch-preview':'1'},slug)
    def test_rollback_pin_is_explicit(self):
        self.assertIn('/app/secrets/bundles/shared.json=MODTALE_CONFIG_SHARED:1',plan('true','dev',{'shared':'1'}))
if __name__=='__main__':unittest.main()
