from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
from secret_bundle import encode
from secret_bundle_store import BundleStore, StoreError, preview_updates, removable_versions

class FakeBackend:
    def __init__(self):
        self.versions={'1':encode({'schemaVersion':1,'boundary':'branch-preview','secrets':{'BRANCH_PREVIEW_MONGODB_URI':'synthetic','branch-preview-modjam-r2-secret-key':'old'}})}
        self.latest='1';self.publishes=0;self.race=False;self.latest_calls=0
    def latest_enabled(self):
        self.latest_calls+=1
        if self.race and self.latest_calls==2:return '99'
        return self.latest
    def read(self,v):return self.versions[v]
    def publish(self,raw):
        self.publishes+=1;self.latest=str(int(self.latest)+1);self.versions[self.latest]=raw;return self.latest

class StoreTests(unittest.TestCase):
    def test_requires_external_lock(self):
        backend=FakeBackend()
        with self.assertRaises(StoreError):BundleStore('branch-preview',backend).update({})
        self.assertEqual(backend.publishes,0)
    def test_noop_does_not_publish(self):
        backend=FakeBackend();store=BundleStore('branch-preview',backend)
        self.assertEqual(store.update({'branch-preview-modjam-r2-secret-key':'old'},lock_held=True),{'version':'1','changed':False})
        self.assertEqual(backend.publishes,0)
    def test_serial_writers_preserve_each_other(self):
        backend=FakeBackend();store=BundleStore('branch-preview',backend)
        store.update({'branch-preview-modjam-r2-secret-key':'new'},lock_held=True)
        store.update({'branch-preview-warden-v3-r2-secret-key':'next'},lock_held=True)
        secrets=store.read('3')['secrets']
        self.assertEqual(secrets['branch-preview-modjam-r2-secret-key'],'new')
        self.assertEqual(secrets['branch-preview-warden-v3-r2-secret-key'],'next')
        self.assertEqual(store.read('1')['secrets']['branch-preview-modjam-r2-secret-key'],'old')
    def test_detects_out_of_band_race(self):
        backend=FakeBackend();backend.race=True
        with self.assertRaises(StoreError):BundleStore('branch-preview',backend).update({'branch-preview-modjam-r2-secret-key':'new'},lock_held=True)
        self.assertEqual(backend.publishes,0)
    def test_complete_preview_set(self):
        credentials={k:'synthetic' for k in ['access-key','secret-key','endpoint','token-id']}
        self.assertEqual(len(preview_updates('branch-preview','modjam',credentials)),4)
        with self.assertRaises(StoreError):preview_updates('branch-preview','modjam',{'access-key':'s'})
    def test_no_cross_boundary_identity(self):
        with self.assertRaises(StoreError):preview_updates('production','modjam',{})
    def test_old_reference_is_retained(self):
        self.assertEqual(removable_versions(['1','2','3'],['1','3'],['2']),[])
        self.assertEqual(removable_versions(['1','2','3'],['1','3'],[]),['2'])
    def test_incomplete_reference_inventory_stops(self):
        with self.assertRaises(StoreError):removable_versions(['2'],['1'],[])
    def test_latest_is_not_a_safe_reference(self):
        with self.assertRaises(ValueError):removable_versions(['1'],['latest'],[])
if __name__=='__main__':unittest.main()
