"""Read-only CI check of fixed resource permissions. Never reads secret payloads."""
import json
import os
from pathlib import Path
import subprocess
import sys

PROJECTS = {
    'main': ('gen-lang-client-0244308719', 'github-branch-preview-deployer@gen-lang-client-0244308719.iam.gserviceaccount.com', ('MODTALE_CONFIG_SHARED','MODTALE_CONFIG_BRANCH_PREVIEW')),
    'pr-preview': ('modtale-pr-preview', 'github-pr-preview-deployer@modtale-pr-preview.iam.gserviceaccount.com', ('MODTALE_CONFIG_PR_PREVIEW',)),
}
SECRET_PERMISSIONS = ['secretmanager.secrets.get','secretmanager.secrets.update','secretmanager.versions.access','secretmanager.versions.add']

class AccessError(RuntimeError):
    pass

def check_access(kind, transport):
    if kind not in PROJECTS:raise AccessError('Unsupported fixed project.')
    project, _, secrets = PROJECTS[kind]
    def call(origin,path,body,fields):
        response=transport({'origin':origin,'path':path,'method':'POST','params':{'fields':fields},'body':body})
        if not isinstance(response,dict) or response.get('status')!=200:raise AccessError('Read-only access check failed; no fallback.')
        return response['body']
    for secret in secrets:
        result=call('https://secretmanager.googleapis.com','/v1/projects/'+project+'/secrets/'+secret+':testIamPermissions',{'permissions':SECRET_PERMISSIONS},'permissions')
        if set(result.get('permissions',[]))!=set(SECRET_PERMISSIONS):raise AccessError('Required existing bundle permission is unavailable.')
    view='projects/'+project+'/locations/global/buckets/_Default/views/modtale-secret-bundle-activation'
    result=call('https://logging.googleapis.com','/v2/'+view+':testIamPermissions',{'permissions':['logging.views.access']},'permissions')
    if result.get('permissions')!=['logging.views.access']:raise AccessError('Scoped activation view is inaccessible.')
    call('https://logging.googleapis.com','/v2/entries:list',{'resourceNames':[view],'filter':'jsonPayload.event="modtale_secret_bundle_activation" AND jsonPayload.activated=true AND labels.modtale_secret_bundle_activation="v1"','pageSize':1},'entries(insertId),nextPageToken')
    return {'project':project,'secret_permissions_verified':list(secrets),'view_access_verified':view,'secret_payloads_read':False}

def main():
    if (os.environ.get('GITHUB_REPOSITORY')!='Modtale/modtale' or os.environ.get('GITHUB_REF')!='refs/heads/main'
        or os.environ.get('GITHUB_EVENT_NAME')!='workflow_dispatch' or os.environ.get('GITHUB_ACTOR')!='Villagers654'
        or os.environ.get('GITHUB_TRIGGERING_ACTOR')!='Villagers654'):
        raise AccessError('Use the fixed owner-only read-only workflow.')
    kind=os.environ.get('BUNDLE_ACCESS_PROJECT','')
    if kind not in PROJECTS:raise AccessError('Unsupported fixed project.')
    expected=PROJECTS[kind][1]
    active=subprocess.run(['gcloud','auth','list','--filter=status:ACTIVE','--format=value(account)'],capture_output=True,text=True,timeout=60)
    if active.returncode or active.stdout.strip()!=expected:raise AccessError('Unexpected authenticated CI identity.')
    sdk=subprocess.run(['gcloud','info','--format=value(installation.sdk_root)'],capture_output=True,text=True,timeout=60)
    sdk_root=Path(sdk.stdout.strip())
    if sdk.returncode or not sdk_root.is_absolute() or not (sdk_root/'lib/googlecloudsdk').is_dir():raise AccessError('Cloud SDK location unavailable.')
    sys.path[:0]=[str(sdk_root/'lib'),str(sdk_root/'lib/third_party')]
    from googlecloudsdk.core.credentials import store
    from google.auth.transport.requests import AuthorizedSession
    session=AuthorizedSession(store.Load(account=expected,allow_account_impersonation=False))
    def transport(request):
        response=session.post(request['origin']+request['path'],params=request['params'],json=request['body'],timeout=60)
        return {'status':response.status_code,'body':response.json() if response.status_code==200 else {}}
    print(json.dumps(check_access(kind,transport)))

if __name__=='__main__':
    try:main()
    except Exception:
        print('Read-only bundle access check failed. No secret payload was requested; details suppressed.',file=sys.stderr)
        sys.exit(1)
