# Security Policy

## Secure Development Practices

We follow established industry best practices for secure development including, but not limited to:

- Secured version control of source code with push restrictions
- Automated testing of security relevant features (e.g. authentication)
- Mandatory peer reviews and CI checks for integrating code into release branches
- Semi-automatic updates of third-party dependencies via Dependabot

## Reporting a security vulnerability

Please report suspected security vulnerabilities in Candlepin privately to
[Red Hat Product Security](mailto:secalert@redhat.com) at **secalert@redhat.com**.
Identify Candlepin and the affected component in your report. If the issue also
affects a Red Hat product, such as Satellite, include that information.

Please do not disclose vulnerabilities through public Jira issues, GitHub issues,
pull requests, mailing lists, forums, or chat while they are being assessed and a
fix is being coordinated.

For encrypted communication, use the current OpenPGP key and instructions on the
[Red Hat Product Security contact page](https://access.redhat.com/security/team/contact).

## What to include

Include as much of the following as you can:

- **Title:** A concise description of the vulnerability and its potential impact.
- **Affected version:** The affected Candlepin version or commit and any relevant dependency versions.
- **Deployment details:** Deployment details, including standalone or hosted mode and relevant configuration.
- **Prerequisites:** Any prerequisites, such as required permissions or access to the system.
- **Reproduction steps:** A minimal example or steps to reproduce the issue.
- **Logs**: Relevant logs or request UUIDs, with credentials, private keys, and sensitive data removed.
- **Suggested fix:** A proposed remediation, if you have one.
- **Disclosure status:** Whether the issue has been disclosed elsewhere and any planned disclosure date.
- **Reporter details:** Your name or handle and affiliation, if you would like to be credited.

You can submit a report even if some of this information is unavailable.

## Supported versions

Currently maintained Candlepin versions are 
- 5.0
- 4.8
- 4.7
- 4.6
- 4.4

## Response and disclosure

Reports are assessed privately, and embargoed fixes are developed in private
branches or forks. All security patches are reviewed by another maintainer with
relevant expertise and tested against the reported issue and regression suites.

Fixes are distributed through normal release channels and backported to all
affected supported branches. Critical and high-severity issues may receive
out-of-band patch releases. Release notes reference the associated CVEs and
advisory. Unsupported branches are not guaranteed security backports.

Maintainers coordinate CVE assignment and disclosure with Red Hat Product
Security, agreeing on disclosure timing with the reporter. Public disclosure
occurs when or shortly after a patched release becomes available. Reporters are
asked to keep details private until the agreed date and are credited if they wish.