#!/usr/bin/env python
#
# USAGE: smoke-test.sh <image_name> <cp_repo_url>
# Example: smoke-test.sh candlepin/candlepin-rhel6 http://download.devel.redhat.com/brewroot/repos/candlepin-mead-rhel-6-build/latest/x86_64

import json
import os
import re
import shlex
import ssl
import subprocess
import sys
import time
import urllib2

from urllib2 import URLError



class RunCommandException(Exception):
    pass

def run_subprocess(p):
    while(True):
        retcode = p.poll()
        line = p.stdout.readline()
        if len(line) > 0:
            yield line
        if(retcode is not None):
            break

def run_command(command, silent=True, ignore_error=False):
    """
    Simliar to run_command but prints each line of output on the fly.
    """
    output = []
    env = os.environ.copy()
    env['LC_ALL'] = 'C'
    p = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        env=env, universal_newlines=True)

    for line in run_subprocess(p):
        line = line.rstrip('\n')
        if not silent: print(line)
        output.append(line)

    if p.poll() > 0 and not ignore_error:
        raise RunCommandException(command, p.poll(), "\n".join(output))

    return output

##################################################

if len(sys.argv) < 3:
    script_file = os.path.basename(__file__)
    print "USAGE: %s <image_name> <cp_repo_url>" % script_file
    print "Example: %s candlepin/candlepin-rhel6 http://download.devel.redhat.com/brewroot/repos/" \
        "candlepin-mead-rhel-6-build/latest/x86_64" % script_file

    sys.exit(1)

image_name = sys.argv[1]
cp_repo_url = sys.argv[2]
max_wait_time = 600
max_log_lines = 50

server_container_id = None
db_container_id = None

success = False
script_start = time.time()

try:
    try:
        try:
            print "Starting containers..."
            db_container_name = "db_%s" % hex(hash(time.time()))[2:]
            output = run_command("docker run -e POSTGRES_PASSWORD=candlepin -d --name %s postgres"
                % db_container_name)
            db_container_id = output[-1]
            time.sleep(3)

            # Launch the candlepin container:
            output = run_command("docker run -P -d -e \"YUM_REPO=%s\" --link %s:db %s"
                % (cp_repo_url, db_container_name, image_name))
            server_container_id = output[-1]
            time.sleep(3)

            # Determine the port used by the CP server...
            output = run_command("docker ps --filter=id=%s" % server_container_id)
            port = None

            regex = re.compile("\\A%12.12s\\s+.*?\\s+.*?\\s+.*?\\s+.*?\\s+.*?:(\\d+)->8443/tcp\\s+.*" %
                server_container_id)

            for line in output:
                match = regex.match(line)

                if match:
                    port = match.group(1)
                    break

            # Wait for it to start...
            if port:
                print "Containers started successfully. Waiting for Candlepin to start..."

                status_url = "https://localhost:%s/candlepin/status" % port
                start_time = time.time()
                remaining = max_wait_time
                response_received = False

                while (remaining > 0):
                    try:
                        response = urllib2.urlopen(status_url, None, remaining)

                        response_received = True
                        code = response.getcode()
                        message = response.read()
                        response.close()

                        print "Response received (code: %s)\n%s\n" % (code, message)
                        if response.getcode() == 200:
                            status = json.loads(message)

                            # TODO: Maybe check some of the fields on status to verify it?
                            success = True
                            print "Received a valid response from the Candlepin server in %.1fs" % \
                                (time.time() - start_time)
                        else:
                            print "Invalid response received"

                        break

                    except URLError as e:
                        # We'll be expecting tons of errors while we wait for CP to start. So, we'll
                        # just silently ignore them and hope for the best.
                        pass

                    time.sleep(1)
                    remaining = max_wait_time - (time.time() - start_time)

                if not response_received:
                    print "Failed to receive a response in %.1fs" % (time.time() - start_time)
                    print "Candlepin container log:"
                    run_command("docker logs --tail=%s %s" % (max_log_lines, server_container_id), False, True)

            else:
                print "ERROR: Unable to determine port for the Candlepin server's container"
        except RunCommandException as e:
            print "ERROR: Error returned from command: %s\n%s" % (e.args[0], e.args[2])

        print "Cleaning up temporary containers..."
    except KeyboardInterrupt:
        # ctrl+c
        print "\nInterrupt received; cleaning up temporary containers..."

    # Make sure we clean up after ourselves, regardless of how far along we were
    if server_container_id:
        run_command("docker stop %s" % server_container_id, True, True)
        run_command("docker rm %s" % server_container_id, True, True)

    if db_container_id:
        run_command("docker stop %s" % db_container_id, True, True)
        run_command("docker rm %s" % db_container_id, True, True)

except KeyboardInterrupt:
    # ANOTHER ctrl+c; this time during cleanup.
    print "\nWARNING: Docker containers may be left in an active state and will need to be " \
        "manually stopped and/or removed."

if not success:
    sys.exit(1)
