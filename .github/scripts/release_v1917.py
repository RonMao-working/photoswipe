"""Publish exact V1.9.17 offline APK; never replace an existing published release."""
import hashlib,json,os
from pathlib import Path
import urllib.error,urllib.request,urllib.parse
REPO='RonMao-working/photoswipe'
TAG='v1.9.17'
NAME='photoswipe-offline-v1.9.17.apk'
SOURCE=Path('.github/release-assets/photoswipe-offline-v1.9.17.apk')
SHA256='9247000366a59e7afbdf5058840fd79c23758124b71358f819b3153b6d6322f1'
SIZE=119832
API='https://api.github.com'

def main():
    if os.environ.get('GITHUB_REPOSITORY')!=REPO or os.environ.get('GITHUB_REF')!='refs/tags/'+TAG:
        raise RuntimeError('Unexpected repository/ref; nothing published.')
    token=os.environ['GITHUB_TOKEN']
    def request(method,path,data=None,binary=False):
        url=path if path.startswith('https://uploads.github.com/') else API+path
        headers={'Authorization':'Bearer '+token,'User-Agent':'PhotoSwipe-release','Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'}
        if data is not None:headers['Content-Type']='application/vnd.android.package-archive' if binary else 'application/json'
        body=data if binary else (json.dumps(data).encode('utf-8') if data is not None else None)
        try:
            with urllib.request.urlopen(urllib.request.Request(url,data=body,headers=headers,method=method),timeout=60) as response:return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code==404 and method=='GET':return None
            raise RuntimeError('GitHub API '+method+' '+('asset-upload' if binary else path)+' HTTP '+str(error.code)) from None
    apk=SOURCE.read_bytes()
    if len(apk)!=SIZE or hashlib.sha256(apk).hexdigest()!=SHA256:raise RuntimeError('APK fingerprint mismatch; nothing published.')
    notes=Path('docs/release-notes-v1.9.17.md').read_text(encoding='utf-8')
    base='/repos/'+REPO+'/releases'
    published=request('GET',base+'/tags/'+TAG)
    if published is not None and not published.get('draft'):
        expected=next((a for a in published.get('assets',[]) if a.get('name')==NAME and a.get('size')==SIZE and a.get('digest')=='sha256:'+SHA256),None)
        if expected:print('Release already verified:',published['html_url']);return
        raise RuntimeError('Published release mismatch; nothing overwritten.')
    releases=request('GET',base+'?per_page=100') or []
    release=published if published else next((r for r in releases if r.get('tag_name')==TAG),None)
    if release is None:
        release=request('POST',base,{'tag_name':TAG,'target_commitish':os.environ['GITHUB_SHA'],'name':'相册滑选（单机版）v1.9.17','body':notes,'draft':True,'prerelease':False})
    if not release.get('draft'):raise RuntimeError('Not a draft; nothing overwritten.')
    assets=request('GET',base+'/'+str(release['id'])+'/assets') or []
    asset=next((a for a in assets if a.get('name')==NAME and a.get('size')==SIZE and a.get('digest')=='sha256:'+SHA256),None)
    if asset is None:
        if assets:raise RuntimeError('Unexpected draft assets; nothing replaced.')
        upload=release['upload_url'].split('{',1)[0]+'?'+urllib.parse.urlencode({'name':NAME})
        asset=request('POST',upload,apk,binary=True)
    if asset.get('name')!=NAME or asset.get('size')!=SIZE or asset.get('state')!='uploaded' or asset.get('digest')!='sha256:'+SHA256:raise RuntimeError('Asset verification failed; remains draft.')
    release=request('PATCH',base+'/'+str(release['id']),{'draft':False})
    if release.get('draft'):raise RuntimeError('Remains unpublished.')
    print('Release published:',release['html_url']);print('APK:',asset['browser_download_url']);print('SHA256:',SHA256)

if __name__=='__main__':
    try:main()
    except Exception as error:
        message=(type(error).__name__+': '+str(error)).replace(os.environ.get('GITHUB_TOKEN','NO_TOKEN'),'[redacted]')
        print('::error::'+message.replace('\n',' ').replace('\r',' '),flush=True);raise SystemExit(1)
