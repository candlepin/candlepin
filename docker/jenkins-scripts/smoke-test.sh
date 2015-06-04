#!/usr/bin/env python
#
# Usage: smoke-test.sh candlepin-rhel6-base http://download.devel.redhat.com/brewroot/repos/candlepin-mead-rhel-6-build/latest/x86_64

import os
import re
import shlex
import subprocess
import sys
import urllib


def run_subprocess(p):
    while(True):
        retcode = p.poll()
        line = p.stdout.readline()
        if len(line) > 0:
            yield line
        if(retcode is not None):
            break

def run_command(command, silent=False):
    """
    Simliar to run_command but prints each line of output on the fly.
    """
    output = []
    env = os.environ.copy()
    env['LC_ALL'] = 'C'
    p = subprocess.Popen(shlex.split(command),
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env,
        universal_newlines=True)
    for line in run_subprocess(p):
        line = line.rstrip('\n')
        if not silent: print(line)
        output.append(line)
    if p.poll() > 0:
        raise RunCommandException(command, p.poll(), "\n".join(output))

    return output





image_name = sys.argv[1]
# cp_repo_url = sys.argv[2]

output = run_command("docker run -e POSTGRES_PASSWORD=candlepin -d postgres")
db_container_id = output[-1]

# output = run_command('docker inspect -f "{{ .Name }}" %s' % db_container_id)
# db_container_name = output[-1]

# Launch the candlepin container:
output = run_command("docker run -P -d --link %s:db %s" % (db_container_id, image_name))
server_container_id = output[-1]

# Determine the port used by the CP server...
output = run_command("docker ps --filter=id=%s" % server_container_id, True)
port = None

regex = re.compile("\\A%12.12s\\s+.*?\\s+.*?\\s+.*?\\s+.*?\\s+.*?:(\\d+)->8443/tcp\\s+.*" % server_container_id)

for line in output:
    match = regex.match(line)

    if match:
        port = match.group(1)
        break


# Wait for it to start...
if port:
    success = False
    status_url = "https://localhost:%s/candlepin/status" % port

    print "HIT THIS URL: %s" % status_url
    # Keep hitting the above URL until a certain amount of time has passed (30-60s?) or we get a
    # positive response back.
    # urllib.request.urlopen();
else:
    print "ERROR: Unable to determine port for the Candlepin server's container"

run_command("docker stop %s" % server_container_id)
run_command("docker stop %s" % db_container_id)
run_command("docker rm %s" % db_container_id)
