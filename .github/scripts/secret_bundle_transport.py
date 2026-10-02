"""Fixed-scope masked REST transport using the CI's existing authentication.

Never extracts tokens or displays response bodies. No secret payload/IAM endpoints
are supported here. Real payload handling remains the separately gated bundle store.
"""
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit

PROJECTS = ('gen-lang-client-0244308719','modtale-pr-preview')
ACCOUNTS = ('github-branch-preview-deployer@gen-lang-client-0244308719.iam.gserviceaccount.com',
            'github-pr-preview-deployer@modtale-pr-preview.iam.gserviceaccount.com')
VIEWS = tuple('projects/'+p+'/locations/global/buckets/_Default/views/modtale-secret-bundle-activation' for p in PROJECTS)

class TransportError(RuntimeError):
    pass

def readiness_scope(request):
    required={'api','origin','method','path','service','service_uid','revision','tag','base_uri'}
    if set(request)!=required or any(not isinstance(value,str) for value in request.values()) or request['api']!='cloud-run-readiness' or request['method']!='HEAD' or request['path']!='/actuator/health/readiness':
        raise TransportError('Only fixed readiness HEAD requests are supported.')
    service=request['service']
    match=re.fullmatch(r'projects/(gen-lang-client-0244308719|modtale-pr-preview)/locations/us-central1/services/([a-z][a-z0-9-]{0,62})',service or '')
    if not match:raise TransportError('Invalid readiness service scope.')
    project,name=match.groups()
    allowed=(name=='modtale-backend' or re.fullmatch(r'modtale-backend-[a-z0-9-]{1,20}',name)) if project==PROJECTS[0] else re.fullmatch(r'modtale-pr-[1-9][0-9]*-backend',name)
    candidate=re.fullmatch(re.escape(name)+r'-(bd|sb)-([a-f0-9]{12})',request['revision'] or '')
    if not allowed or not candidate:
        raise TransportError('Invalid readiness candidate identity.')
    if not re.fullmatch(r'(?:sb|sr)-[a-f0-9]{12}',request['tag'] or '') or not re.fullmatch(r'[A-Za-z0-9-]{1,128}',request['service_uid'] or ''):
        raise TransportError('Invalid readiness ownership metadata.')
    prefix='sb-' if candidate.group(1)=='bd' else 'sr-'
    if request['tag']!=prefix+candidate.group(2):raise TransportError('Readiness tag does not belong to its candidate.')
    parsed=[]
    for value in (request['base_uri'],request['origin']):
        if not isinstance(value,str) or any(ord(c)<33 or ord(c)>126 for c in value):
            raise TransportError('Invalid readiness URL.')
        try:
            uri=urlsplit(value)
            port=uri.port
        except ValueError:raise TransportError('Invalid readiness URL.') from None
        if uri.scheme!='https' or uri.username or uri.password or port or uri.path or uri.query or uri.fragment or not uri.hostname or value!='https://'+uri.hostname:
            raise TransportError('Only canonical HTTPS Cloud Run origins are supported.')
        parsed.append(uri.hostname)
    base,tagged=parsed
    if not re.fullmatch(re.escape(name)+r'-[a-z0-9-]+\.a\.run\.app',base) or tagged!=request['tag']+'---'+base:
        raise TransportError('Readiness URL does not match its exact owned Cloud Run tag.')
    return project

def readiness_head(session,request,transient_errors=(),fatal_errors=()):
    """No authentication, redirects, headers or response content are returned."""
    readiness_scope(request)
    session.cookies.clear()
    response=None
    try:
        response=session.request('HEAD',request['origin']+request['path'],timeout=45,allow_redirects=False,stream=True)
        return {'status':response.status_code,'body':{}}
    except fatal_errors:raise
    except transient_errors:return {'status':0,'body':{}}
    finally:
        if response is not None:response.close()
        session.cookies.clear()

def validate_request(request):
    if not isinstance(request,dict):raise TransportError('Invalid request.')
    api=request.get('api');origin=request.get('origin');method=request.get('method');path=request.get('path')
    if not isinstance(path,str) or not path.startswith('/') or any(x in path for x in ('?','#','..','\\')):
        raise TransportError('Invalid fixed API path.')
    if api=='cloud-run-readiness':
        readiness_scope(request)
        return
    if api=='github':
        from secret_bundle_preview_inventory import BRANCH_QUERY,PR_QUERY
        if origin!='https://api.github.com' or method!='POST' or path!='/graphql' or request.get('query') not in (BRANCH_QUERY,PR_QUERY):
            raise TransportError('Unsupported GitHub query.')
        if set(request.get('variables',{}))!={'cursor'}:raise TransportError('Unexpected GraphQL variables.')
        return
    if api=='github-public-run':
        if origin!='https://api.github.com' or method!='GET' or not re.fullmatch(r'/repos/Modtale/modtale/actions/runs/[1-9][0-9]*',path):
            raise TransportError('Unsupported public run metadata request.')
        return
    params=request.get('params',{})
    fields=params.get('fields')
    if not isinstance(fields,str) or not fields or '*' in fields:
        raise TransportError('Server-side response mask is required.')
    from secret_bundle_preview_inventory import SERVICE_FIELDS,REVISION_FIELDS,SERVICE_LIST_FIELDS,REVISION_LIST_FIELDS,OWNERSHIP_FIELDS,CONFIG_FIELDS
    allowed={SERVICE_FIELDS,REVISION_FIELDS,SERVICE_LIST_FIELDS,REVISION_LIST_FIELDS,OWNERSHIP_FIELDS,CONFIG_FIELDS,'name','name,etag','name,uid,etag','name,uid,createTime','name,etag,annotations','versions(name,state),nextPageToken','name,state','entries(insertId),nextPageToken','name,done,error(code)','done,error(code)','name,done','name,uid,service,createTime,serviceAccount,'+CONFIG_FIELDS}
    try:
        from secret_bundle_rollout import SERVICE_FIELDS as rollout_service,REVISION_FIELDS as rollout_revision
        allowed.update((rollout_service,rollout_revision,rollout_service+',uri,ingress'))
    except ModuleNotFoundError:
        pass
    allowed.update(('name,uid,conditions(type,state)','name,uid,generation,trafficStatuses(type,revision,percent,tag,uri)'))
    if fields not in allowed:raise TransportError('Unsupported response mask.')
    scope=r'projects/(?:gen-lang-client-0244308719|modtale-pr-preview)/locations/us-central1'
    if api=='cloud-run-v2':
        if origin!='https://run.googleapis.com' or method not in ('GET','PATCH','DELETE') or not re.fullmatch(r'/v2/'+scope+r'/(?:services(?:/[a-z][a-z0-9-]{0,62}(?:/revisions(?:/[a-z][a-z0-9-]{0,62})?)?)?|operations/[A-Za-z0-9_-]{1,128})',path):
            raise TransportError('Unsupported Cloud Run scope.')
        if method!='GET':
            name=path.rsplit('/',1)[-1]
            project=path.split('/')[3]
            valid=(name=='modtale-backend' or name.startswith('modtale-backend-')) if project==PROJECTS[0] else bool(re.fullmatch(r'modtale-pr-[1-9][0-9]*-backend',name))
            if method=='DELETE':
                valid=(bool(re.fullmatch(r'modtale-(?:backend|frontend)-[a-z0-9-]{1,20}',name)) and name not in ('modtale-backend-dev','modtale-frontend-dev')) if project==PROJECTS[0] else bool(re.fullmatch(r'modtale-pr-[1-9][0-9]*-(?:backend|frontend)',name))
                if not params.get('etag'):raise TransportError('Conditional preview deletion required.')
            if not valid:raise TransportError('Only fixed scoped service mutations are supported.')
            if method=='PATCH':
                if params.get('updateMask') not in ('traffic','template.revision,template.volumes,traffic','traffic,ingress'):
                    raise TransportError('Unsupported runtime mutation mask.')
                body=request.get('body',{})
                if set(body)-{'name','etag','template','traffic','ingress'} or not body.get('etag') or body.get('name')!=path[len('/v2/'):] or set(body.get('template',{}))-{'revision','volumes'}:
                    raise TransportError('Conditional rollout body required.')
    elif api=='cloud-run-v1-ownership':
        if origin!='https://us-central1-run.googleapis.com' or method!='GET' or not re.fullmatch(r'/apis/serving.knative.dev/v1/namespaces/(?:gen-lang-client-0244308719|modtale-pr-preview)/services',path) or fields!=OWNERSHIP_FIELDS:
            raise TransportError('Unsupported ownership request.')
        selector=params.get('labelSelector','')
        if not re.fullmatch(r'app=modtale,environment=(?:branch-preview|pr-preview),component=(?:backend|frontend)(?:,branch=[a-z0-9-]{1,20})?',selector):
            raise TransportError('Unexpected ownership selector.')
    elif api=='secret-manager-metadata':
        from secret_bundle import TARGETS
        roots={'/v1/projects/'+p+'/secrets/'+name for p,name in TARGETS.values()}
        if origin!='https://secretmanager.googleapis.com' or method not in ('GET','PATCH') or path not in roots:
            raise TransportError('Only fixed bundle container metadata is supported.')
        if fields not in (('name,etag,annotations',) if method=='GET' else ('name,etag','name,etag,annotations')):raise TransportError('Unexpected journal response mask.')
        if method=='PATCH' and (params.get('updateMask')!='annotations' or set(request.get('body',{}))-{'name','etag','annotations'} or not request.get('body',{}).get('etag')):
            raise TransportError('Conditional metadata-only journal update required.')
    elif api=='cloud-logging':
        if origin!='https://logging.googleapis.com' or method!='POST' or path!='/v2/entries:list' or fields!='entries(insertId),nextPageToken':
            raise TransportError('Unsupported activation evidence request.')
        body=request.get('body',{})
        if body.get('resourceNames') not in ([v] for v in VIEWS):raise TransportError('Only approved filtered views may be read.')
        if 'jsonPayload.event="modtale_secret_bundle_activation"' not in body.get('filter','') or 'labels.modtale_secret_bundle_activation="v1"' not in body.get('filter',''):
            raise TransportError('Exact activation marker filter required.')
    else:raise TransportError('Unsupported metadata API.')

def create_transport():
    if os.environ.get('GITHUB_REPOSITORY')!='Modtale/modtale':raise TransportError('Trusted repository CI context required.')
    active=subprocess.run(['gcloud','auth','list','--filter=status:ACTIVE','--format=value(account)'],capture_output=True,text=True,timeout=60)
    account=active.stdout.strip()
    if active.returncode or account not in ACCOUNTS:raise TransportError('Unexpected existing CI identity.')
    result=subprocess.run(['gcloud','info','--format=value(installation.sdk_root)'],capture_output=True,text=True,timeout=60)
    root=Path(result.stdout.strip())
    if result.returncode or not root.is_absolute() or not (root/'lib/googlecloudsdk').is_dir():raise TransportError('Installed Cloud SDK unavailable.')
    sys.path[:0]=[str(root/'lib'),str(root/'lib/third_party')]
    from googlecloudsdk.core.credentials import store
    from google.auth.transport.requests import AuthorizedSession
    import requests
    google=AuthorizedSession(store.Load(account=account,allow_account_impersonation=False))
    public=requests.Session()
    readiness=requests.Session()
    readiness.trust_env=False  # Never attach ambient netrc credentials to a canary.
    token=os.environ.get('GITHUB_TOKEN','') or os.environ.get('GH_TOKEN','')
    def transport(request):
        validate_request(request)
        project=PROJECTS[ACCOUNTS.index(account)]
        if request['api']=='cloud-run-readiness':
            if readiness_scope(request)!=project:raise TransportError('Authenticated readiness project boundary mismatch.')
        elif request['api'].startswith('cloud-run') or request['api']=='secret-manager-metadata':
            expected='/namespaces/'+project+'/' if request['api']=='cloud-run-v1-ownership' else '/projects/'+project+'/'
            if expected not in request['path']:raise TransportError('Authenticated project boundary mismatch.')
        if request['api']=='cloud-logging' and request['body']['resourceNames']!=[VIEWS[PROJECTS.index(project)]]:
            raise TransportError('Authenticated log-view boundary mismatch.')
        try:
            api=request['api'];kwargs={'timeout':90,'allow_redirects':False}
            if api=='cloud-run-readiness':
                return readiness_head(readiness,request,(requests.exceptions.Timeout,requests.exceptions.ConnectionError),(requests.exceptions.SSLError,))
            elif api=='github':
                if not token:raise TransportError('Existing GitHub token is required for lifecycle reads.')
                kwargs.update(json={'query':request['query'],'variables':request['variables']},headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json'})
                response=public.request('POST',request['origin']+request['path'],**kwargs)
            elif api=='github-public-run':
                response=public.get(request['origin']+request['path'],**kwargs)
            else:
                kwargs['params']=request['params']
                if 'body' in request:kwargs['json']=request['body']
                response=google.request(request['method'],request['origin']+request['path'],**kwargs)
            return {'status':response.status_code,'body':response.json() if response.status_code==200 else {}}
        except Exception:
            raise TransportError('API request failed; uncertain writes must be reconciled from durable state.') from None
    return transport
