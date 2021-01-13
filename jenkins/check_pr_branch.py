#!/usr/bin/env python
from __future__ import unicode_literals

from configparser import ConfigParser
import argparse
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

config = ConfigParser()
with open(os.path.join(os.path.expanduser('~'), 'automation.properties'), 'rb') as config_file:
    file_content = config_file.read().decode('utf-8')
    config_contents = '[defaults]\n{}'.format(file_content)

config.read_string(config_contents)

bugzilla_user = config.get('defaults', 'bugzilla.login')
bugzilla_password = config.get('defaults', 'bugzilla.password')
bugzilla_url = config.get('defaults', 'bugzilla.url')

github_token = os.environ.get('GITHUB_TOKEN_PSW')
if not github_token:
    raise EnvironmentError('GITHUB_TOKEN not specified')


parser = argparse.ArgumentParser(description='check that a candlepin PR references the correct BZ')
parser.add_argument('pr', help='the pr number to examine')
args = parser.parse_args()

# fetch pr
pr = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}'.format(pr=args.pr),
                  headers={'Authorization': 'token {}'.format(github_token)}).json()
target = pr['base']['ref']

bz = Bugzilla(bugzilla_url, user=bugzilla_user, password=bugzilla_password)

master_version = None
def fetch_master_version():
    global master_version
    if master_version:
        return master_version
    spec_file = requests.get('https://raw.githubusercontent.com/candlepin/candlepin/master/server/candlepin.spec.tmpl').text
    for line in spec_file.split('\n'):
        if line.startswith('Version:'):
            match = re.search('^Version: (\d+\.\d+)\.\d+$', line)
            version = match.group(1)
            master_version = version
            return version

version = None
if target == 'master':
    # fetch master spec and then parse from it
    version = fetch_master_version()
else:
    version_match = re.search('^candlepin-(.*)-HOTFIX$', target)
    if version_match:
        version = version_match.group(1)
if not version:
    print('Skipping because target branch is not master or HOTFIX branch')
    sys.exit(0)

pr_commits = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}/commits'.format(pr=args.pr),
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
            target_release = fetch_master_version()


        final_version = target_release or bug.version
        if final_version != version:
            print(UH_OH)
            print('{commit}: BZ#{bz_number} references {final_version}, while PR references {version}'.format(commit=commit['sha'], bz_number=bz_number, final_version=final_version, version=version))
            sys.exit(1)
        print('{commit} looks good, both BZ and PR reference {version}').format(commit=commit['sha'], version=version)
    else:
        print('{commit} does not appear to reference a BZ number.'.format(commit=commit['sha']))
