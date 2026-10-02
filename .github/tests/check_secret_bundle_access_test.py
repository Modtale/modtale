from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
import check_secret_bundle_access as a
class AccessTests(unittest.TestCase):
    def test_fixed_permissions_and_view_reads_only(self):
        for kind in a.PROJECTS:
            calls=[]
            def transport(r):
                calls.append(r)
                return {'status':200,'body':{'permissions':r['body'].get('permissions',[])}}
            result=a.check_access(kind,transport)
            self.assertFalse(result['secret_payloads_read'])
            for call in calls:
                self.assertTrue(call['path'].endswith(':testIamPermissions') or call['path']=='/v2/entries:list')
                self.assertIn('fields',call['params'])
                self.assertNotIn(':access',call['path'])
                self.assertNotIn(':add',call['path'])
                self.assertNotIn(':setIamPolicy',call['path'])
            self.assertEqual(calls[-1]['body']['resourceNames'],[result['view_access_verified']])
    def test_denial_stops_without_fallback(self):
        calls=[]
        def transport(r):calls.append(r);return {'status':403,'body':{}}
        with self.assertRaises(a.AccessError):a.check_access('main',transport)
        self.assertEqual(len(calls),1)
    def test_missing_permission_fails_closed(self):
        with self.assertRaises(a.AccessError):a.check_access('main',lambda r:{'status':200,'body':{'permissions':[]}})
    def test_arbitrary_project_rejected(self):
        calls=[]
        with self.assertRaises(a.AccessError):a.check_access('other',lambda r:calls.append(r))
        self.assertEqual(calls,[])
if __name__=='__main__':unittest.main()
