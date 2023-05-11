#!/usr/bin/env python3
from __future__ import unicode_literals

import os
import re
import sys

from bugzilla import Bugzilla
import requests

UH_OH = '''
       _                 _     _
 _   _| |__         ___ | |__ | |
| | | | '_ \ _____ / _ \| '_ \| |
| |_| | | | |_____| (_) | | | |_|
 \__,_|_| |_|      \___/|_| |_(_)
'''

github_token = os.environ.get('GITHUB_TOKEN_PSW')
if not github_token:
    raise EnvironmentError('GITHUB_TOKEN not specified')

pr_number = os.environ.get('PR_NUMBER')
if not github_token:
    raise EnvironmentError('PR_NUMBER not specified')

pr = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}'.format(pr=pr_number),
                  headers={'Authorization': 'token {}'.format(github_token)}).json()
target = pr['base']['ref']

bz = Bugzilla('bugzilla.redhat.com')

main_version = None
def fetch_main_version():
    global main_version
    if main_version:
        return main_version
    spec_file = requests.get('https://raw.githubusercontent.com/candlepin/candlepin/main/candlepin.spec.tmpl').text
    for line in spec_file.split('\n'):
        if line.startswith('Version:'):
            match = re.search('^Version: (\d+\.\d+)\.\d+$', line)
            version = match.group(1)
            main_version = version
            return version

version = None
if target == 'main':
    # fetch main spec and then parse from it
    version = fetch_main_version()
else:
    version_match = re.search('^candlepin-(.*)-HOTFIX$', target)
    if version_match:
        version = version_match.group(1)
if not version:
    print('Skipping because target branch is not main or HOTFIX branch')
    sys.exit(0)

pr_commits = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}/commits'.format(pr=pr_number),
                          headers={'Authorization': 'token {}'.format(github_token)}).json()

for commit in pr_commits:
    message = commit['commit']['message']

    first_line = message.split('\n')[0]

    match = re.search('^(\d+):? ', first_line)
    if match:
        bz_number = match.group(1)
        bug = bz.getbug(bz_number)

        target_release = bug.target_release[0]
        if '---' in target_release:
            target_release = fetch_main_version()


        final_version = target_release or bug.version
        if final_version != version:
            print(UH_OH)
            print('{commit}: BZ#{bz_number} references {final_version}, while PR references {version}'.format(commit=commit['sha'], bz_number=bz_number, final_version=final_version, version=version))
            sys.exit(1)
        print('{commit} looks good, both BZ and PR reference {version}'.format(commit=commit['sha'], version=version))
    else:
        print('{commit} does not appear to reference a BZ number.'.format(commit=commit['sha']))
