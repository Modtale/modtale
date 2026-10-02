import importlib.util
import json
from pathlib import Path
import unittest

p = Path(__file__).parents[1] / 'scripts/secret_bundle.py'
spec = importlib.util.spec_from_file_location('secret_bundle', p)
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)

def raw(boundary='shared', **secrets):
    return json.dumps({'schemaVersion': 1, 'boundary': boundary, 'secrets': secrets}).encode()

class BundleTests(unittest.TestCase):
    def test_all_three_boundaries(self):
        for boundary, key in [('shared','MONGODB_URI'), ('branch-preview','BRANCH_PREVIEW_MONGODB_URI'), ('pr-preview','PREVIEW_MONGODB_URI')]:
            self.assertEqual(b.parse(raw(boundary, **{key: 'synthetic'}), boundary)['secrets'][key], 'synthetic')
    def test_cross_boundary_rejected(self):
        for boundary in ('shared','branch-preview','pr-preview'):
            with self.assertRaises(b.BundleError): b.parse(raw(boundary, HYTALE_CLIENT_SECRET='synthetic'), boundary)
    def test_bad_inputs_do_not_echo_value(self):
        for value in [b'{"secret":"SYNTHETIC_SENTINEL"',raw(MONGODB_URI='SYNTHETIC_SENTINEL') + b' trailing', b'\xff']:
            with self.assertRaises(b.BundleError) as caught: b.parse(value,'shared')
            self.assertNotIn('SYNTHETIC_SENTINEL',str(caught.exception))
    def test_duplicate_keys(self):
        with self.assertRaises(b.BundleError): b.parse(b'{"schemaVersion":1,"boundary":"shared","secrets":{"MONGODB_URI":"a","MONGODB_URI":"b"}}','shared')
    def test_invalid_envelope(self):
        for value in [b'[]',b'null',b'{}', b'{"schemaVersion":true,"boundary":"shared","secrets":{"MONGODB_URI":"s"}}']:
            with self.assertRaises(b.BundleError): b.parse(value,'shared')
    def test_size(self):
        with self.assertRaises(b.BundleError): b.parse(raw(MONGODB_URI='x'*65536),'shared')
    def test_literal_preservation(self):
        value='${DO_NOT_EXPAND}\\comma,equals=unicode☃\nnewline'
        self.assertEqual(b.parse(raw(MONGODB_URI=value),'shared')['secrets']['MONGODB_URI'],value)
    def test_numeric_pins_only(self):
        for value in ['latest','0','-1','1x','',1]:
            with self.assertRaises(b.BundleError): b.numeric_version(value)
        self.assertEqual(b.numeric_version('123'),'123')
    def test_branch_patch_preserves_other_entries(self):
        base=raw('branch-preview', BRANCH_PREVIEW_MONGODB_URI='s', **{'branch-preview-modjam-r2-secret-key':'old','branch-preview-warden-v3-r2-secret-key':'keep'})
        updated=b.patch_snapshot(base,'branch-preview',{'branch-preview-modjam-r2-secret-key':'new'})
        parsed=b.parse(updated,'branch-preview')['secrets']
        self.assertEqual(parsed['branch-preview-warden-v3-r2-secret-key'],'keep')
        self.assertEqual(parsed['branch-preview-modjam-r2-secret-key'],'new')
        self.assertEqual(b.parse(base,'branch-preview')['secrets']['branch-preview-modjam-r2-secret-key'],'old')
    def test_dynamic_names_are_isolated(self):
        with self.assertRaises(b.BundleError): b.parse(raw('branch-preview', **{'pr-preview-1-r2-access-key':'s'}),'branch-preview')
        with self.assertRaises(b.BundleError): b.parse(raw('pr-preview', **{'branch-preview-modjam-r2-access-key':'s'}),'pr-preview')
    def test_invalid_values(self):
        for value in ['',None,False,{},'a\x00b','\ud800']:
            with self.assertRaises(b.BundleError): b.parse(raw(MONGODB_URI=value),'shared')
    def test_rollback_is_original_snapshot(self):
        original=raw(MONGODB_URI='before')
        updated=b.patch_snapshot(original,'shared',{'MONGODB_URI':'after'})
        self.assertNotEqual(updated,original)
        self.assertEqual(b.parse(original,'shared')['secrets']['MONGODB_URI'],'before')

if __name__ == '__main__': unittest.main()
