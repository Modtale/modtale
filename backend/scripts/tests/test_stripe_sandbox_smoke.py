import importlib.util
import pathlib
import unittest
import tempfile
import json
import io
from urllib.error import HTTPError
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('runner',pathlib.Path(__file__).parents[1]/'stripe-sandbox-smoke.py')
runner=importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
class SandboxGuardTest(unittest.TestCase):
 def env(self, key='sk_test_fixture'):
  return {'MODTALE_STRIPE_SANDBOX_OPT_IN':'true','STRIPE_SECRET_KEY':key,'STRIPE_PLATFORM_ACCOUNT_ID':'acct_fixture'}
 def test_opt_in_required(self):
  env=self.env();env.pop('MODTALE_STRIPE_SANDBOX_OPT_IN')
  with self.assertRaises(ValueError): runner.validate_environment(env)
 def test_live_unknown_empty_keys_refused(self):
  for key in ['sk_live_fixture','rk_live_fixture','unknown','']:
   with self.subTest(key=key), self.assertRaises(ValueError): runner.validate_environment(self.env(key))
 def test_only_recognized_test_keys(self):
  for key in ['sk_test_fixture','rk_test_fixture','rkcs_fixture']:
   self.assertEqual(key,runner.validate_environment(self.env(key))[0])
 def test_api_version_matches_production_gateway(self):
  path=pathlib.Path(runner.__file__).parents[1]/'src/main/java/net/modtale/service/finance/StripeGatewayService.java'
  self.assertIn('API_VERSION = "'+runner.API_VERSION+'"',path.read_text())
 def test_exact_account_required(self):
  env=self.env();env['STRIPE_PLATFORM_ACCOUNT_ID']='acct_fixture/path'
  with self.assertRaises(ValueError): runner.validate_environment(env)
 def test_provider_object_mode_is_explicit(self):
  for live in [None,True,'false',0]:
   with self.assertRaises(ValueError): runner.SandboxRun.test_object({'id':'pi_fixture','livemode':live},'pi_')
 def test_provider_object_id_is_bounded(self):
  for object_id in ['cs_fixture','pi_bad/path','pi_']:
   with self.assertRaises(ValueError): runner.SandboxRun.test_object({'id':object_id,'livemode':False},'pi_')
 def test_no_arbitrary_error_data_in_audit(self):
  for value in ['secret key foo',{'value':'secret'},'https://claim.example','secret\nmore']:
   self.assertIsNone(runner.safe_code(value))
  self.assertEqual('permission_error',runner.safe_code('permission_error'))
 def test_known_test_object_accepted(self):
  self.assertEqual('pi_fixture',runner.SandboxRun.test_object({'id':'pi_fixture','livemode':False},'pi_'))
class SandboxTransportGuardTest(unittest.TestCase):
 def make_run(self, directory): return runner.SandboxRun('sk_test_fixture_never_print', 'acct_fixture', pathlib.Path(directory))
 def test_invalid_paths_never_open_network(self):
  with tempfile.TemporaryDirectory() as directory:
   run=self.make_run(directory)
   for path in ['https://elsewhere.test','/account','../account','account#fragment']:
    with patch.object(runner.urllib.request,'build_opener') as opener, self.assertRaises(ValueError): run.request('GET',path)
    opener.assert_not_called()
 def test_provider_body_and_key_are_never_saved_on_failure(self):
  with tempfile.TemporaryDirectory() as directory:
   run=self.make_run(directory)
   body=json.dumps({'error':{'type':'invalid_request_error','message':'sk_test_fixture_never_print','code':'sk_test_fixture_never_print not a code'}}).encode()
   with patch.object(runner.urllib.request,'build_opener') as factory:
    factory.return_value.open.side_effect=HTTPError('https://api.stripe.com/v1/account',403,'Forbidden',{},io.BytesIO(body))
    with self.assertRaises(ValueError): run.request('GET','account')
   audit=(pathlib.Path(directory)/'report.json').read_text()
   self.assertNotIn('sk_test_fixture_never_print',audit);self.assertIn('invalid_request_error',audit)
 def test_lost_response_is_indeterminate_and_never_retried(self):
  with tempfile.TemporaryDirectory() as directory:
   run=self.make_run(directory)
   with patch.object(runner.urllib.request,'build_opener') as factory:
    factory.return_value.open.side_effect=TimeoutError()
    with self.assertRaises(ValueError): run.request('POST','payment_intents',{'amount':'500'})
    self.assertEqual(1,factory.return_value.open.call_count)
   self.assertEqual('OUTCOME_UNKNOWN',run.report['last_request']['status'])
 def test_non_anonymous_account_denial_stops_before_mutation(self):
  with tempfile.TemporaryDirectory() as directory:
   run=self.make_run(directory)
   with patch.object(run,'request',return_value=(403,{})) as request:
    with self.assertRaises(ValueError):run.execute()
    self.assertEqual(1,request.call_count)
 def test_wrong_account_stops_before_mutation(self):
  with tempfile.TemporaryDirectory() as directory:
   run=self.make_run(directory)
   with patch.object(run,'request',return_value=(200,{'object':'account','id':'acct_wrong'})) as request:
    with self.assertRaises(ValueError):run.execute()
    self.assertEqual(1,request.call_count)
 def test_pending_fee_is_not_a_pass_or_substituted_estimate(self):
  source=pathlib.Path(runner.__file__).read_text()
  self.assertIn('No estimated fee is substituted',source)
if __name__=='__main__':unittest.main()
