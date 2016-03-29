#!/usr/bin/env python
#
# USAGE: smoke-test.py <image_name> <cp_repo_url>
# Example: smoke-test.py candlepin/candlepin-rhel6-base http://download.devel.redhat.com/brewroot/repos/candlepin-mead-rhel-6-build/latest/x86_64

import json
import os
import re
import shlex
import ssl
import subprocess
import sys
import time
import urllib2

from optparse import OptionParser
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

parser = OptionParser()
parser.add_option("-t", "--time",
  action="store", dest="wait_time", default="600",
  help="Amount of time to wait for a response from Candlepin; defaults to 600")
parser.add_option("-l", "--log-lines",
  action="store", dest="log_lines", default="all",
  help="The number of log lines to print on failure or if a response isn't received; defaults to \"all\"")
parser.add_option("-r", "--rpm",
  action="store_true", dest="rpm_url", default=False,
  help="Whether or not the repo URL should be treated as a link to an RPM")

(options, args) = parser.parse_args()


if len(args) < 2:
    script_file = os.path.basename(__file__)
    print "USAGE: %s [-t #][-l #] <image_name> <cp_repo_url>" % script_file
    print "Example: %s candlepin/candlepin-rhel6 http://download.devel.redhat.com/brewroot/repos/" \
        "candlepin-mead-rhel-6-build/latest/x86_64" % script_file

    sys.exit(1)

image_name = args[0]
cp_repo_url = args[1]
max_wait_time = int(options.wait_time) if re.match("\\A0*[123456789]\\d*\\Z", options.wait_time) else 600
max_log_lines = options.log_lines if re.match("\\A(?:0*[123456789]\\d*)|(?:all)\\Z", options.wait_time) else "all"
env_var_name = "RPM_URL" if options.rpm_url else "YUM_REPO"

server_container_id = None
db_container_id = None

success = False
script_start = time.time()

# Try to disable SSL hostname checking if on a newer Python version
# that does so by default. If this fails because of an AttributeError
# we're likely on older Python, which doesn't check hostnames by default.
ssl_context = None
try:
    ssl_context = ssl.SSLContext(ssl.PROTOCOL_SSLv23)
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE
except AttributeError:
    pass

try:
    try:
        try:
            print "Starting containers..."
            db_container_name = "db_%s" % hex(hash(time.time()))[2:]
            output = run_command("docker run -e POSTGRES_PASSWORD=candlepin -d --name %s postgres"
                % db_container_name)
            db_container_id = output[-1]
            print "Postgres container: %s" % db_container_id
            # Postgres might need to be fully up if we're testing a container that has candlepin
            # preinstalled, thus cpdb run happens *quick*. Give postgres a little time:
            time.sleep(10)

            # Launch the candlepin container:
            output = run_command("docker run -P -d -e \"%s=%s\" --link %s:db %s"
                % (env_var_name, cp_repo_url, db_container_name, image_name))
            server_container_id = output[-1]
            print "Candlepin container: %s" % server_container_id
            time.sleep(3)

            # Determine the port used by the CP server...
            regex = re.compile(".*:(\\d+)\\s*\\Z")
            output = run_command("docker port %s 8443/tcp" % server_container_id)
            match = regex.match(output[0])

            if match:
                port = match.group(1)

                # Wait for it to start...
                print "Containers started successfully. Waiting for Candlepin to respond on port %s..." % port

                status_url = "https://localhost:%s/candlepin/status" % port
                start_time = time.time()
                remaining = max_wait_time
                response_received = False

                while (remaining > 0):
                    try:
                        if ssl_context:
                            response = urllib2.urlopen(status_url, None, remaining, context=ssl_context)
                        else:
                            response = urllib2.urlopen(status_url, None, remaining)

                        response_received = True
                        code = response.getcode()
                        message = response.read()
                        response.close()

                        print "Response received (code: %s):\n%s\n" % (code, message)
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
                        # We're expecting a bunch of connection resets, but we may want to catch
                        # some of the other issues
                        if not e.reason.errno in [8, 104]:
                            print "ERROR: Unable to query Candlpin status: %s" % e
                            break

                    remaining = int(round(max_wait_time - (time.time() - start_time)))

                    if remaining > 0:
                        print "No response; will continue re-trying for another %s seconds..." % remaining
                        time.sleep(10 if remaining > 10 else remaining)

                if not response_received:
                    print "Failed to receive a response in %.1fs" % (int(time.time() - start_time))
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
