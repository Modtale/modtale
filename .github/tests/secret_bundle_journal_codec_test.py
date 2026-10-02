"""All encoded records here are synthetic metadata, never credentials."""
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
from secret_bundle_journal_codec import CodecError, scoped_record_bytes, parse_scoped_record

FULL='projects/gen-lang-client-0244308719/locations/us-central1/services/modtale-backend-dev'
REVISION=FULL+'/revisions/modtale-backend-dev-bd-0123456789ab'
OPERATION=FULL.rsplit('/services/',1)[0]+'/operations/0123456789-ab'

class CodecTests(unittest.TestCase):
    def test_exact_round_trip_for_service_revision_operation_and_opaque_etags(self):
        record={'service':FULL,'candidate':{'name':REVISION,'service':FULL,'uid':'uid-one'},
                'operation':OPERATION,'etag':'"'+'e'*131+'"','other':['untouched','é']}
        raw=scoped_record_bytes(record,FULL)
        self.assertNotIn(FULL.encode(),raw)
        self.assertEqual(parse_scoped_record(raw,FULL),record)
        self.assertLess(len(raw),len(json.dumps(record).encode()))

    def test_full_and_short_revision_references_restore_distinct_exact_forms(self):
        short=REVISION.rsplit('/',1)[1]
        record={'full':REVISION,'short':short,'service':FULL,'unrelated':'other-service-bd-0123456789ab'}
        raw=scoped_record_bytes(record,FULL)
        self.assertIn(b'"$revision":"bd-0123456789ab"',raw)
        self.assertIn(b'"$short_revision":"bd-0123456789ab"',raw)
        self.assertEqual(parse_scoped_record(raw,FULL),record)

    def test_maximum_etag_is_bounded_without_unknown_value_interning(self):
        record={'base':{'etag':'"'*1024,'uid':'u'*128},'staged':{'etag':'x'*1024,'uid':'v'*128}}
        raw=scoped_record_bytes(record,FULL)
        self.assertIn(b'u'*128,raw);self.assertIn(b'v'*128,raw)
        self.assertEqual(parse_scoped_record(raw,FULL),record)
        with self.assertRaises(CodecError):scoped_record_bytes({'etag':'x'*1025},FULL)

    def test_caller_fields_cannot_impersonate_reserved_markers(self):
        for marker,value in [('$service',1),('$revision','name'),('$short_revision','name'),('$operation','id'),('$etag','eA==')]:
            with self.assertRaises(CodecError):scoped_record_bytes({'nested':{marker:value}},FULL)
            with self.assertRaises(CodecError):parse_scoped_record(json.dumps({marker:value,'extra':0}).encode(),FULL)

    def test_invalid_and_cross_service_reference_markers_fail_closed(self):
        for raw in [b'{"$service":true}',b'{"$short_revision":"../bad"}',
                    b'{"$revision":"../bad"}',b'{"$operation":"../operation"}',
                    b'{"$etag":"not base64"}']:
            with self.assertRaises(CodecError):parse_scoped_record(raw,FULL)

    def test_duplicate_keys_nonfinite_numbers_and_expansion_are_bounded(self):
        for raw in [b'{"a":1,"a":2}',b'{"value":NaN}']:
            with self.assertRaises(CodecError):parse_scoped_record(raw,FULL)
        aliases=json.dumps([{'$revision':'bd-0123456789ab'}]*1000).encode()
        self.assertLess(len(aliases),65536)
        with self.assertRaises(CodecError):parse_scoped_record(aliases,FULL)
        with self.assertRaises(CodecError):scoped_record_bytes([{'revision':REVISION}]*1000,FULL)
        with self.assertRaises(CodecError):scoped_record_bytes({'large':'x'*65536},FULL)

    def test_scope_must_stay_in_fixed_projects_and_region(self):
        for scope in [FULL.replace('gen-lang-client-0244308719','other-project'),FULL.replace('us-central1','europe-west1')]:
            with self.assertRaises(CodecError):scoped_record_bytes({},scope)
            with self.assertRaises(CodecError):parse_scoped_record(b'{}',scope)

if __name__=='__main__':unittest.main()
