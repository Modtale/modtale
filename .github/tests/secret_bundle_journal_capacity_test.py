import copy,sys,json,unittest
from secret_bundle_ci_test import DeploymentAPI,IMAGE,ENV
import secret_bundle_ci_deploy as d
import secret_bundle_rollout as r
from unittest.mock import patch
class Model(DeploymentAPI):
 def __init__(self,profile,identifier=None):
  self.profile,self.identifier=profile,identifier
  self.boundary,self.secret_short,self.short,self.target_full=r.scope(profile,identifier)
  super().__init__()
 def stage(self,name,pin):
  name=name.replace('modtale-backend-alpha',self.short)
  self.full=self.target_full
  self.secret='projects/'+self.full.split('/')[1]+'/secrets/'+self.secret_short
  super().stage(name,pin)
  self.service['ingress']='INGRESS_TRAFFIC_ALL'
  self.service['uri']='https://'+self.short+'-synthetic-uc.a.run.app'
  self.service['uid']='a774a283-1111-2222-3333-444444444444'
  self.service['etag']='"'+'e'*131+'"'
  self.service['latestCreatedRevision']=self.full+'/revisions/'+name
  self.service['latestReadyRevision']=self.full+'/revisions/'+name
  self.service['template']['revision']=self.full+'/revisions/'+name
  self.service['template']['containers'][0]['name']=self.short+'-1'
  self.service['template']['containers'][0]['volumeMounts'][0]['name']='secret-volume-0123456789abcdef0123456789abcdef'
  self.service['template']['volumes'][0]['name']='secret-volume-0123456789abcdef0123456789abcdef'
  self.service['template']['volumes'][0]['secret']['secret']=self.secret
  self.service['template']['volumes'][0]['secret']['items'][0]['path']=self.boundary+'.json'
  self.revisions[name].update(name=self.full+'/revisions/'+name,uid='bbbbbbbb-1111-2222-3333-444444444444',service=self.full,
   createTime='2026-10-02T14:00:00.123456789Z',**{key:copy.deepcopy(self.service['template'][key]) for key in ('containers','volumes')})
 def ci(self,env=None):
  return super().ci({'ENV_TYPE':self.profile,'BRANCH_SLUG':self.identifier or '',
   'BACKEND_SERVICE':self.short,'RUNTIME_SERVICE_ACCOUNT':d.RUNTIME_IDENTITIES[self.profile],
   'GIT_BRANCH_NAME':self.identifier if self.identifier else 'develop' if self.profile=='dev' else 'main',**(env or {})})

class JournalCapacityTests(unittest.TestCase):
 def make(self,profile,identifier=None):
  model=Model(profile,identifier)
  d.deploy(model.ci(),['--image',IMAGE])
  return model

 def assert_group(self,models):
  # Deliberately retain all terminal entries to test the codec reservation itself.
  eviction_patch=patch.object(d,'completed_journal_evictions',return_value=[])
  eviction_patch.start();self.addCleanup(eviction_patch.stop)
  group={'modtale-initial-dev-v1':'m'*(690-len('modtale-initial-dev-v1'))}
  for model in models:group.update(model.annotations)
  maxima=[]
  for index,model in enumerate(models):
   model.annotations=copy.deepcopy(group);model.crash='after-stage';model.commands.clear()
   with self.assertRaises(RuntimeError):d.deploy(model.ci({'GITHUB_RUN_ID':str(9999+index)}),['--image',IMAGE])
   current=d.DeploymentJournal(model.ci()).load()[0]
   self.assertEqual(current['schema'],3)
   self.assertEqual(current['phase'],'stage_intent')
   maxima.append(sum(len(k.encode())+len(v.encode()) for k,v in model.annotations.items()))
   self.assertLess(maxima[-1],16384)
   for other in models:
    if other is not model:
     key=d.DeploymentJournal(other.ci()).key
     self.assertEqual(model.annotations[key],group[key])
   d.deploy(model.ci({'GITHUB_RUN_ID':str(9999+index)}),['--image',IMAGE])
   self.assertEqual(d.DeploymentJournal(model.ci()).load()[0]['phase'],'complete')
   self.assertEqual(len(model.commands),1)
   self.assertEqual(model.annotations['modtale-initial-dev-v1'],group['modtale-initial-dev-v1'])
   group=copy.deepcopy(model.annotations)
  return maxima

 def test_shared_dev_and_prod_coexist_with_preserved_manual_record_and_resume(self):
  self.assertLess(max(self.assert_group([self.make('dev'),self.make('prod')])),15000)

 def test_three_existing_branch_consumers_coexist_and_resume_serial_updates(self):
  models=[self.make('branch-preview',name) for name in ('modjam','monetization','warden-v3')]
  self.assertLess(max(self.assert_group(models)),15500)

 def test_three_maximum_length_branch_consumers_fit_with_foreign_metadata(self):
  self.assertLess(max(self.assert_group([self.make('branch-preview',letter*20) for letter in 'abc'])),16384)

 def test_metadata_exhaustion_stops_before_staging_and_retains_all_annotations(self):
  model=self.make('dev');model.annotations['foreign']='x'*6000;original=copy.deepcopy(model.annotations)
  model.commands.clear()
  with self.assertRaises(Exception):d.deploy(model.ci({'GITHUB_RUN_ID':'9999'}),['--image',IMAGE])
  self.assertEqual(model.annotations,original);self.assertEqual(model.commands,[])

if __name__=='__main__':unittest.main()
