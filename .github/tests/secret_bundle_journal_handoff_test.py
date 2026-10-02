"""Synthetic scoped journal compatibility and atomic same-service handoffs."""
import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch
from secret_bundle_ci_test import FanoutAPI, DeploymentAPI, ENV, IMAGE, ci, deploy, rollout

class JournalHandoffTests(unittest.TestCase):
    def subject(self, api, **env):
        return ci.BundleCI(api.transport,{**ENV,**env},runner=api.runner,sleeper=lambda _:None)

    def finish(self, executor):
        for _ in range(12):
            result=executor.advance(**ci.LOCK)
            if result['status'] in ('complete','rolled_back'):return result
        self.fail('Synthetic rollout failed to settle')

    def test_frozen_schema2_checkpoint_decodes_and_supersedes_without_losing_provenance(self):
        data=json.loads((Path(__file__).parent/'fixtures/ordinary_schema2_checkpoint.json').read_text())
        api=DeploymentAPI();api.annotations=data['annotations'];api.service=data['service'];api.revisions=data['revisions']
        journal=deploy.DeploymentJournal(api.ci());old,checksum=journal.load()
        self.assertEqual(old['schema'],2)
        self.assertEqual(checksum,__import__('hashlib').sha256(deploy.deployment_record_bytes(old)).hexdigest())
        deploy.deploy(api.ci({'GITHUB_RUN_ID':'9999','GITHUB_SHA':'c'*40}),['--image',IMAGE])
        new,_=journal.load()
        self.assertEqual(new['schema'],3);self.assertEqual(new['serving_metadata'],old['serving_metadata'])
        self.assertIn(old['revision'],api.revisions)
        self.assertEqual(new['retained_revisions'][0],api.revisions[old['revision']])

    def test_complete_ordinary_transfers_atomically_to_mount_then_back(self):
        api=FanoutAPI();subject=self.subject(api)
        deploy.deploy(subject,['--image',IMAGE]);ordinary=deploy.DeploymentJournal(subject)
        old,_=ordinary.load();api.requests.clear()
        subject.fanout('3')
        mount=rollout.SecretManagerJournal(api.transport,'branch-preview','alpha')
        self.assertNotIn(ordinary.key,api.annotations);self.assertIn(mount.key,api.annotations)
        first=next(r for r in api.requests if r['api']=='secret-manager-metadata' and r['method']=='PATCH')
        self.assertNotIn(ordinary.key,first['body']['annotations']);self.assertIn(mount.key,first['body']['annotations'])
        completed,_=mount.load();api.requests.clear()
        deploy.deploy(self.subject(api,GITHUB_RUN_ID='9999'),['--image',IMAGE])
        self.assertNotIn(mount.key,api.annotations);self.assertIn(ordinary.key,api.annotations)
        adopted,_=ordinary.load()
        self.assertEqual(adopted['serving_metadata'],completed['candidate'])
        self.assertIn(old['revision'],api.revisions)
        first=next(r for r in api.requests if r['api']=='secret-manager-metadata' and r['method']=='PATCH')
        self.assertNotIn(mount.key,first['body']['annotations']);self.assertIn(ordinary.key,first['body']['annotations'])

    def test_rolled_back_mount_handoff_adopts_saved_positive_serving_revision(self):
        api=FanoutAPI();subject=self.subject(api);subject.fanout('2')
        mount=rollout.SecretManagerJournal(api.transport,'branch-preview','alpha')
        executor=rollout.RolloutExecutor(api.transport,mount,subject.inventory.github_lifecycle)
        executor.rollback(**ci.LOCK);self.assertEqual(self.finish(executor)['status'],'rolled_back')
        completed,_=mount.load();recovery=api.service['latestCreatedRevision'];serving=completed['base_revision']
        self.assertFalse(any(r['revision']==recovery and r['percent']>0 for r in api.service['traffic']))
        deploy.deploy(self.subject(api,GITHUB_RUN_ID='9999'),['--image',IMAGE])
        record,_=deploy.DeploymentJournal(subject).load()
        self.assertEqual(record['serving_metadata'],serving)
        self.assertIn(recovery,api.revisions);self.assertNotIn(mount.key,api.annotations)

    def test_mount_to_ordinary_cas_rejection_preserves_source_and_stops_before_cli(self):
        api=FanoutAPI();subject=self.subject(api);subject.fanout('2')
        original=copy.deepcopy(api.annotations);transport=api.transport;api.commands.clear()
        def reject(req):
            if req['api']=='secret-manager-metadata' and req['method']=='PATCH':return {'status':412,'body':{}}
            return transport(req)
        subject.transport=reject
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.annotations,original);self.assertEqual(api.commands,[])

    def test_mount_to_ordinary_capacity_failure_preserves_source_and_stops_before_cli(self):
        api=FanoutAPI();subject=self.subject(api);subject.fanout('2');api.annotations['foreign']='x'*6000
        original=copy.deepcopy(api.annotations);api.commands.clear()
        with self.assertRaises(ci.CIError):deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.annotations,original);self.assertEqual(api.commands,[])

    def test_crash_after_atomic_transfer_resumes_from_ordinary_checkpoint(self):
        api=FanoutAPI();subject=self.subject(api);subject.fanout('2');api.crash='before-stage'
        mount=rollout.SecretManagerJournal(api.transport,'branch-preview','alpha')
        with self.assertRaises(RuntimeError):deploy.deploy(subject,['--image',IMAGE])
        self.assertNotIn(mount.key,api.annotations)
        record,_=deploy.DeploymentJournal(subject).load();self.assertEqual(record['phase'],'stage_intent')
        deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(deploy.DeploymentJournal(subject).load()[0]['phase'],'complete')

    def test_ordinary_to_mount_rejects_drifted_terminal_before_any_mutation(self):
        api=FanoutAPI();subject=self.subject(api);deploy.deploy(subject,['--image',IMAGE])
        original=copy.deepcopy(api.annotations);api.service['uid']='different-service';api.requests.clear()
        with self.assertRaises(ci.CIError):subject.fanout('3')
        self.assertEqual(api.annotations,original)
        self.assertFalse(any(r['method']=='PATCH' for r in api.requests))

    def test_deleted_preview_releases_only_padding_after_lifecycle_and_both_absent(self):
        api=FanoutAPI();subject=self.subject(api);api.crash='after-stage'
        with self.assertRaises(RuntimeError):deploy.deploy(subject,['--image',IMAGE])
        journal=deploy.DeploymentJournal(subject);record,checksum=journal.load();source=api.annotations[journal.key]
        api.annotations['foreign']='retained';api.service=None
        subject.inventory.github_lifecycle=lambda _: {'repository':ci.plan.REPOSITORY,'complete':True,'items':[]}
        subject.release_absent_preview_padding()
        self.assertEqual(api.annotations[journal.key],source.split('.',1)[0])
        self.assertEqual(journal.load(),(record,checksum));self.assertEqual(api.annotations['foreign'],'retained')
        self.assertEqual(len(api.revisions),2)

    def test_padding_is_not_released_while_preview_still_exists(self):
        api=FanoutAPI();subject=self.subject(api);api.crash='after-stage'
        with self.assertRaises(RuntimeError):deploy.deploy(subject,['--image',IMAGE])
        original=copy.deepcopy(api.annotations)
        subject.inventory.github_lifecycle=lambda _: {'repository':ci.plan.REPOSITORY,'complete':True,'items':[]}
        with self.assertRaises(ci.CIError):subject.release_absent_preview_padding()
        self.assertEqual(api.annotations,original)

    def test_mount_source_checksum_change_rejects_atomic_transfer(self):
        api=FanoutAPI();subject=self.subject(api);subject.fanout('2');transport=api.transport;armed=False
        source=rollout.SecretManagerJournal(api.transport,'branch-preview','alpha');original=api.annotations[source.key]
        def change(req):
            result=transport(req)
            if req['api']=='secret-manager-metadata' and req['method']=='GET' and armed:
                result['body']['annotations'].pop(source.key,None)
            return result
        original_save=deploy.DeploymentJournal.save
        def save(journal,record,checksum):
            nonlocal armed
            armed=True
            return original_save(journal,record,checksum)
        subject.transport=change
        with patch.object(deploy.DeploymentJournal,'save',save),self.assertRaises(ci.CIError):
            deploy.deploy(subject,['--image',IMAGE])
        self.assertEqual(api.annotations[source.key],original);self.assertEqual(api.commands,[])

if __name__=='__main__':unittest.main()
