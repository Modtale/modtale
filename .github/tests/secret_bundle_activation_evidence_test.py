import copy
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
import secret_bundle_activation_evidence as e

CREATED = '2026-10-02T12:00:00.123456789Z'

class EvidenceTests(unittest.TestCase):
    def test_exact_revision_profile_project_and_safe_mask(self):
        cases=[('prod','modtale-backend-00001-abc',None),('dev','modtale-backend-dev-00001-abc',None),('branch-preview','modtale-backend-modjam-00001-abc','modjam'),('pr-preview','modtale-pr-123-backend-00001-abc','123')]
        for profile,revision,preview in cases:
            request=e.activation_request(profile,revision,CREATED,preview)
            self.assertEqual(request['params'],{'fields':'entries(insertId),nextPageToken'})
            self.assertIn('resource.labels.revision_name="'+revision+'"',request['body']['filter'])
            self.assertIn('jsonPayload.profile="'+profile+'"',request['body']['filter'])
            self.assertIn('jsonPayload.activated=true',request['body']['filter'])
            self.assertIn('timestamp>="'+CREATED+'"',request['body']['filter'])
            self.assertNotIn('env',json.dumps(request))
            self.assertNotIn('Authorization',json.dumps(request))
            result=e.verify_activation(lambda r:{'status':200,'body':{'entries':[{'insertId':'safe-id'}]}},profile,revision,CREATED,preview)
            self.assertTrue(result['verified_active'])
    def test_absence_is_unverified_never_disabled(self):
        result=e.verify_activation(lambda r:{'status':200,'body':{}},'dev','modtale-backend-dev-00001-abc',CREATED)
        self.assertFalse(result['verified_active'])
        self.assertNotIn('disabled',str(result))
    def test_empty_pages_continue_until_actual_termination(self):
        requests=[]
        def api(request):
            requests.append(copy.deepcopy(request))
            return {'status':200,'body':{'nextPageToken':'next'} if len(requests)==1 else {'entries':[{'insertId':'id'}]}}
        self.assertTrue(e.verify_activation(api,'dev','modtale-backend-dev-00001-abc',CREATED)['verified_active'])
        self.assertEqual(requests[1]['body']['pageToken'],'next')
    def test_permission_errors_have_no_fallback_or_retry(self):
        for status in (401,403,429,500):
            calls=[]
            def api(r):
                calls.append(r)
                return {'status':status,'body':{'SYNTHETIC_PRIVATE':'never echo'}}
            result=e.verify_activation(api,'dev','modtale-backend-dev-00001-abc',CREATED)
            self.assertEqual(result['http_status'],status)
            self.assertFalse(result['verified_active'])
            self.assertEqual(len(calls),1)
            self.assertNotIn('SYNTHETIC',str(result))
    def test_malformed_or_payload_responses_fail_closed(self):
        for body in ({'entries':[{'insertId':'id','jsonPayload':{'SYNTHETIC_PRIVATE':'x'}}]}, {'entries':'x'}, {'entries':[{}]}, {'nextPageToken':None}, {'entries':[{'insertId':'unsafe\nline'}]}, {'entries':[{'insertId':'ok'},{'insertId':1}]}, {'entries':[{'insertId':'\ud800'}]}, {'nextPageToken':'bad\n'}, {'nextPageToken':'\ud800'}):
            with self.assertRaises(e.EvidenceError) as caught:e.verify_activation(lambda r:{'status':200,'body':body},'dev','modtale-backend-dev-00001-abc',CREATED)
            self.assertNotIn('SYNTHETIC',str(caught.exception))
    def test_scope_and_filter_injection_rejected_before_transport(self):
        for args in [('other','modtale-backend-dev-1-a',None),('prod','modtale-backend-dev-1-a','bad'),('dev','bad" OR severity>=0',None),('pr-preview','modtale-pr-1-backend-1-a','1"'),('branch-preview','modtale-backend-main-1-a','main')]:
            calls=[]
            with self.assertRaises(ValueError):e.verify_activation(lambda r:calls.append(r),args[0],args[1],CREATED,args[2])
            self.assertEqual(calls,[])
    def test_invalid_creation_time_rejected_before_transport(self):
        for value in (None,'','2026-02-30T00:00:00Z','2026-10-02T00:00:00Z" OR severity>=0','2026-10-02','2026-10-02T00:00:00+02:00'):
            calls=[]
            with self.assertRaises(e.EvidenceError):e.verify_activation(lambda r:calls.append(r),'dev','modtale-backend-dev-00001-abc',value)
            self.assertEqual(calls,[])
    def test_repeated_tokens_fail_closed(self):
        with self.assertRaises(e.EvidenceError):e.verify_activation(lambda r:{'status':200,'body':{'nextPageToken':'repeat'}},'dev','modtale-backend-dev-00001-abc',CREATED)
    def test_transport_failure_does_not_echo_cause(self):
        def api(r):raise RuntimeError('SYNTHETIC_PRIVATE')
        with self.assertRaises(e.EvidenceError) as caught:e.verify_activation(api,'dev','modtale-backend-dev-00001-abc',CREATED)
        self.assertIsNone(caught.exception.__cause__)
        self.assertNotIn('SYNTHETIC',str(caught.exception))
if __name__=='__main__':unittest.main()
