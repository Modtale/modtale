import copy
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).parents[1]/'scripts'))
import secret_bundle_transport as t
import secret_bundle_preview_inventory as i
import secret_bundle_activation_evidence as e

class TransportTests(unittest.TestCase):
    def run_read(self):
        return {'api':'cloud-run-v2','origin':'https://run.googleapis.com','method':'GET','path':'/v2/projects/gen-lang-client-0244308719/locations/us-central1/services/modtale-backend-dev','params':{'fields':i.SERVICE_FIELDS}}
    def test_known_masks_and_fixed_endpoints(self):
        t.validate_request(self.run_read())
        t.validate_request(e.activation_request('dev','modtale-backend-dev-00001-abc','2026-10-02T00:00:00Z'))
        t.validate_request({'api':'github','origin':'https://api.github.com','method':'POST','path':'/graphql','query':i.BRANCH_QUERY,'variables':{'cursor':None}})
    def test_never_accepts_unmasked_environment_values(self):
        for mask in ('','*','name,template','name,template(containers(env(name,value)))','name,annotations'):
            r=self.run_read();r['params']['fields']=mask
            with self.assertRaises(t.TransportError):t.validate_request(r)
    def test_never_accepts_other_hosts_projects_or_regions(self):
        for key,value in [('origin','https://example.invalid'),('path','/v2/projects/other/locations/us-central1/services/modtale-backend-dev'),('path','/v2/projects/gen-lang-client-0244308719/locations/europe-west1/services/modtale-backend-dev'),('path','/v2/../metadata'),('path','/v2/projects/gen-lang-client-0244308719/locations/us-central1/services/modtale-backend-dev?alt=media')]:
            r=self.run_read();r[key]=value
            with self.assertRaises(t.TransportError):t.validate_request(r)
    def test_fixed_journal_metadata_only(self):
        r={'api':'secret-manager-metadata','origin':'https://secretmanager.googleapis.com','method':'GET','path':'/v1/projects/gen-lang-client-0244308719/secrets/MODTALE_CONFIG_SHARED','params':{'fields':'name,etag,annotations'}}
        t.validate_request(r)
        for suffix in ('/versions/1:access',':addVersion',':setIamPolicy'):
            changed=copy.deepcopy(r);changed['path']+=suffix
            with self.assertRaises(t.TransportError):t.validate_request(changed)
        changed=copy.deepcopy(r);changed['path']=changed['path'].replace('MODTALE_CONFIG_SHARED','MONGODB_URI')
        with self.assertRaises(t.TransportError):t.validate_request(changed)
    def test_log_project_wide_or_markerless_reads_rejected(self):
        request=e.activation_request('dev','modtale-backend-dev-00001-abc','2026-10-02T00:00:00Z')
        for key,value in [('resourceNames',['projects/gen-lang-client-0244308719']),('filter','severity>=0')]:
            r=copy.deepcopy(request);r['body'][key]=value
            with self.assertRaises(t.TransportError):t.validate_request(r)
    def test_conditional_patch_cannot_change_environment_or_identity(self):
        r=self.run_read();r.update(method='PATCH',params={'fields':'name','updateMask':'template.revision,template.volumes,traffic'},body={'name':r['path'][len('/v2/'):],'etag':'expected','template':{'revision':'new','volumes':[]},'traffic':[]})
        t.validate_request(r)
        for change in ('missing-etag','env-body','env-mask','wrong-name'):
            request=copy.deepcopy(r)
            if change=='missing-etag':request['body'].pop('etag')
            elif change=='env-body':request['body']['template']['containers']=[]
            elif change=='env-mask':request['params']['updateMask']='template.containers'
            else:request['body']['name']='projects/other'
            with self.assertRaises(t.TransportError):t.validate_request(request)
    def test_no_default_credentials_outside_trusted_ci(self):
        with patch.dict(os.environ,{},clear=True),patch.object(t.subprocess,'run') as command:
            with self.assertRaises(t.TransportError):t.create_transport()
            command.assert_not_called()
    def test_production_service_deletion_rejected(self):
        request=self.run_read();request.update(method='DELETE',params={'fields':'name,done,error(code)','etag':'expected'})
        with self.assertRaises(t.TransportError):t.validate_request(request)
        request['path']=request['path'].removesuffix('-dev')
        with self.assertRaises(t.TransportError):t.validate_request(request)
    def test_public_run_reads_are_fixed_and_never_fallback(self):
        t.validate_request({'api':'github-public-run','origin':'https://api.github.com','method':'GET','path':'/repos/Modtale/modtale/actions/runs/123'})
        with self.assertRaises(t.TransportError):t.validate_request({'api':'github-public-run','origin':'https://api.github.com','method':'GET','path':'/repos/other/repo/actions/runs/123'})
if __name__=='__main__':unittest.main()
