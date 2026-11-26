#!/usr/bin/env python3
from __future__ import unicode_literals

import os
import re
import sys

from atlassian import Jira
from authlib.integrations.requests_client import OAuth2Session

import requests

TARGET_VERSION_FIELD_ID = 'customfield_10855'
TARGET_VERSION_FIELD_NAME = 'Target Version'

github_token = os.environ.get('GITHUB_TOKEN_PSW')
if not github_token:
    raise EnvironmentError('GITHUB_TOKEN_PSW not specified')

pr_number = os.environ.get('PR_NUMBER')
if not pr_number:
    raise EnvironmentError('PR_NUMBER not specified')

pr = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}'.format(pr=pr_number),
                  headers={'Authorization': 'token {}'.format(github_token)}).json()
target = pr['base']['ref']

pr_target_version = None
if target == 'main':
    pr_target_version = ''
else:
    version_match = re.search(r'^candlepin-(.*)-HOTFIX$', target)
    if version_match:
        pr_target_version = version_match.group(1)
    if not pr_target_version:
        print('Skipping because target branch is not main or HOTFIX branch')
        sys.exit(0)

pr_commits = requests.get('https://api.github.com/repos/candlepin/candlepin/pulls/{pr}/commits'.format(pr=pr_number),
                          headers={'Authorization': 'token {}'.format(github_token)}).json()

jira_keys = []
for commit in pr_commits:
    first_line = commit['commit']['message'].split('\n', 1)[0]
    jira_key_match = re.match(r'CANDLEPIN-\d+', first_line)
    if jira_key_match:
        jira_keys.append(jira_key_match.group())

if not jira_keys:
    sys.exit(0)

for name in ('CANDLEPIN_JIRA_CLIENT_ID', 'CANDLEPIN_JIRA_CLIENT_SECRET', 'CANDLEPIN_JIRA_CLOUD_ID'):
    if not os.environ.get(name):
        raise EnvironmentError(f'{name} not provided')

session = OAuth2Session(
    client_id=os.environ['CANDLEPIN_JIRA_CLIENT_ID'],
    client_secret=os.environ['CANDLEPIN_JIRA_CLIENT_SECRET'],
    token_endpoint='https://auth.atlassian.com/oauth/token',
    token_endpoint_auth_method='client_secret_post',
    grant_type='client_credentials',
    default_timeout=30,
)
session.fetch_token()
jira = Jira(f"https://api.atlassian.com/ex/jira/{os.environ['CANDLEPIN_JIRA_CLOUD_ID']}", session=session, cloud=True)

for jira_key in jira_keys:
    issue = jira.issue(jira_key)

    contain_sat_link = any(
        link.get('type', {}).get('inward') == 'is depended on by' and
        link.get('inwardIssue', {}).get('key', '').startswith('SAT-')
        for link in issue.get("fields", {}).get("issuelinks", [])
    )

    if not contain_sat_link:
        continue

    jira_target_versions = issue.get('fields', {}).get(TARGET_VERSION_FIELD_ID, {})

    # If there is no Jira target version field value defined for the Jira task then the task is expected to merge into main
    if not jira_target_versions:
        if not pr_target_version:
            continue
        else:
            print("Expected PR target branch to be '" + pr_target_version + "' for '" + jira_key + "', but was main")
            sys.exit(1)

    # It is an error state to have more than one target versions defined for a Jira issue
    if len(jira_target_versions) > 1:
        print("More than one Jira version defined for '" + TARGET_VERSION_FIELD_NAME + "' field for '" + jira_key + "'")
        sys.exit(1)

    jira_target_version = jira_target_versions[0]["name"]

    if jira_target_version != pr_target_version:
        # Changing to 'main' for better logging
        if not pr_target_version:
            pr_target_version = 'main'

        print("PR target branch '" + pr_target_version + "' does not equal the target version field value '" + jira_target_version + "' for Jira issue '" + jira_key + "'")
        sys.exit(1)
