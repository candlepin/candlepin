#!/usr/bin/env python
#
# Usage: smoke-test.sh candlepin-rhel6-base http://download.devel.redhat.com/brewroot/repos/candlepin-mead-rhel-6-build/latest/x86_64

import os
import shlex
import subprocess
import sys

image = sys.argv[1]
cp_repo_url = sys.argv[2]

print(image)
print(cp_repo_url)

def run_command(command):
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
        print(line)
        output.append(line)
    print("\n"),
    if p.poll() > 0:
        raise RunCommandException(command, p.poll(), "\n".join(output))
    return '\n'.join(output)


def run_subprocess(p):
    while(True):
        retcode = p.poll()
        line = p.stdout.readline()
        if len(line) > 0:
            yield line
        if(retcode is not None):
            break

container_id = run_command("docker run -e POSTGRES_PASSWORD=candlepin -d postgres")
print("Got container id: %s" % container_id)

container_name = run_command('docker inspect -f "{{ .Name }}" %s' % container_id)

# Launch the candlepin container:
container_id = run_command("docker run -i -P -t --rm --link loving_nobel:db candlepin-rhel6-base")

run_command("docker stop %s" % container_id)
run_command("docker rm %s" % container_id)

