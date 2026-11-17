"""Publish exactly the delivered v1.9.15 APK using the job-scoped GitHub token.
No builds, credential storage, deletion, or replacement of published assets.
"""
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import urllib.parse

REPO = 'RonMao-working/photoswipe'
TAG = 'v1.9.15'
NAME = '相册滑选-v1.9.15.apk'
SOURCE = Path('.github/release-assets/相册滑选-v1.9.15.apk')
SHA256 = '2b32bdd132a8f437250dd7a41c88fc8e38f3cbfaf743b39a0ceba60cfdf50ee5'
SIZE = 119832
API = 'https://api.github.com'


def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO or os.environ.get('GITHUB_REF') not in ('refs/tags/' + TAG, 'refs/heads/main'):
        raise SystemExit('Unexpected repository or ref; nothing published.')
    token = os.environ['GITHUB_TOKEN']
    def request(method, path, data=None, binary=False):
        url = path if path.startswith('https://uploads.github.com/') else API + path
        headers = {'Authorization': 'Bearer ' + token, 'User-Agent': 'PhotoSwipe-release',
                   'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}
        if data is not None:
            headers['Content-Type'] = 'application/vnd.android.package-archive' if binary else 'application/json'
        body = data if binary else (json.dumps(data).encode('utf-8') if data is not None else None)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404 and method == 'GET':
                return None
            raise RuntimeError('GitHub API request failed: HTTP ' + str(error.code)) from None

    # Read the exact delivered APK from the repository; no external storage dependency.
    print('Stage: verify repository APK', flush=True)
    apk = SOURCE.read_bytes()
    if len(apk) != SIZE or hashlib.sha256(apk).hexdigest() != SHA256:
        raise SystemExit('APK size or SHA-256 mismatch; nothing published.')
    notes = Path('docs/release-notes-v1.9.15.md').read_text(encoding='utf-8')
    base = '/repos/' + REPO + '/releases'
    print('Stage: check existing release', flush=True)
    release = request('GET', base + '/tags/' + TAG)
    if release is not None:
        raise SystemExit('A release already exists; it has not been overwritten.')
    print('Stage: create draft release', flush=True)
    release = request('POST', base, {'tag_name': TAG, 'target_commitish': os.environ['GITHUB_SHA'],
                                   'name': '相册滑选 v1.9.15', 'body': notes, 'draft': True, 'prerelease': False})
    upload = release['upload_url'].split('{', 1)[0] + '?' + urllib.parse.urlencode({'name': NAME})
    asset = request('POST', upload, apk, binary=True)
    if asset.get('name') != NAME or asset.get('size') != SIZE or asset.get('state') != 'uploaded':
        raise SystemExit('Upload verification failed; release remains a draft.')
    digest = asset.get('digest')
    if digest and digest != 'sha256:' + SHA256:
        raise SystemExit('GitHub asset digest mismatch; release remains a draft.')
    release = request('PATCH', base + '/' + str(release['id']), {'draft': False})
    if release.get('draft'):
        raise SystemExit('Release remains unpublished.')
    print('Release published:', release['html_url'])
    print('APK:', asset['browser_download_url'])
    print('SHA-256:', SHA256)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        message = (type(error).__name__ + ': ' + str(error)).replace(os.environ.get('GITHUB_TOKEN', 'NO_TOKEN'), '[redacted]')
        print('::error::' + message.replace('\n', ' ').replace('\r', ' '), flush=True)
        raise SystemExit(1)
