#
# Copyright (c) 2009 Red Hat, Inc.
#
# This software is licensed to you under the GNU General Public License,
# version 2 (GPLv2). There is NO WARRANTY for this software, express or
# implied, including the implied warranties of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
# along with this software; if not, see
# http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
#
# Red Hat trademarks are not licensed under GPLv2. No permission is
# granted to use or replicate Red Hat trademarks that are incorporated
# in this software or its documentation.
#

"""
Command line interface for managing entitlements with the candlepin server.
"""

import sys
import os
import random
import commands
import ConfigParser
import httplib, urllib

from optparse import OptionParser
from string import strip

SCRIPT_DIR = os.path.abspath(os.path.join(os.path.dirname(
        os.path.abspath(sys.argv[0])), "../"))

#from spacewalk.releng.builder import Builder, NoTgzBuilder
#from spacewalk.releng.tagger import VersionTagger, ReleaseTagger
#from spacewalk.releng.common import DEFAULT_BUILD_DIR
#from spacewalk.releng.common import find_git_root, run_command, \
#        error_out, debug, get_project_name, get_relative_project_dir, \
#        check_tag_exists, get_latest_tagged_version

#BUILD_PROPS_FILENAME = "build.py.props"
GLOBAL_BUILD_PROPS_FILENAME = "candlepin.conf"
GLOBALCONFIG_SECTION = "globalconfig"
#DEFAULT_BUILDER = "default_builder"
#DEFAULT_TAGGER = "default_tagger"
#ASSUMED_NO_TAR_GZ_PROPS = """
#[buildconfig]
#builder = spacewalk.releng.builder.NoTgzBuilder
#tagger = spacewalk.releng.tagger.ReleaseTagger
#"""

def get_class_by_name(name):
    """
    Get a Python class specified by it's fully qualified name.

    NOTE: Does not actually create an instance of the object, only returns
    a Class object.
    """
    # Split name into module and class name:
    tokens = name.split(".")
    class_name = tokens[-1]
    module = ""

    for s in tokens[0:-1]:
        if len(module) > 0:
            module = module + "."
        module = module + s

    mod = __import__(tokens[0])
    components = name.split('.')
    for comp in components[1:-1]:
        mod = getattr(mod, comp)

    debug("Importing %s" % name)
    c = getattr(mod, class_name)
    return c

def read_user_config():
    config = {}
    file_loc = os.path.expanduser("~/.candlepinrc")
    try:
        f = open(file_loc)
    except:
        # File doesn't exist but that's ok because it's optional.
        return config

    for line in f.readlines():
        if line.strip() == "":
            continue
        tokens = line.split("=")
        if len(tokens) != 2:
            raise Exception("Error parsing ~/.candlepinrc: %s" % line)
        config[tokens[0]] = strip(tokens[1])
    return config

class CLI:
    """
    Parent command line interface class.

    Simply delegated to sub-modules which group appropriate command line
    options together.
    """

    def main(self):
        if len(sys.argv) < 2 or not CLI_MODULES.has_key(sys.argv[1]):
            self._usage()
            sys.exit(1)

        module_class = CLI_MODULES[sys.argv[1]]["class"]
        module = module_class()
        module.main()

    def _usage(self):
        print("Usage: %s MODULENAME --help" %
                (os.path.basename(sys.argv[0])))
        print("Supported modules:")
        
        for key in CLI_MODULES:
            pad = len(max(CLI_MODULES, key=len))
            print("  %s - %s" % (key.rjust(pad), CLI_MODULES[key]["help"]))
        
class BaseCliModule(object):
    """ Common code used amongst all CLI modules. """

    def __init__(self):
        self.parser = None
        self.global_config = None
        self.options = None
        self.pkg_config = None
        self.user_config = read_user_config()
        self.server_url = "localhost:8080"

    def _set_server(self, url):
        if url is not None:
            if not url.startswith("http://"):
                url = "http://" + url
            #if not url.endswith("/"):
            #    url = url + "/"
        self.server_url = url
        
    def _add_common_options(self):
        """
        Add options to the command line parser which are relevant to all
        modules.
        """
        # Options used for many different activities:
        self.parser.add_option("--debug", dest="debug", action="store_true",
                help="print debug messages", default=False)
        self.parser.add_option("--offline", dest="offline", action="store_true",
                help="do not attempt any remote communication (avoid using " +
                    "this please)",
                default=False)
        self.parser.add_option("--server_url", nargs=1, dest="url",
                help="Candlepin Server URL i.e. localhost, localhost:8080, \
                    http://server.com:8080/")

    def main(self):
        (self.options, args) = self.parser.parse_args()

        self._validate_options()

        if len(sys.argv) < 2:
            print parser.error("Must supply an argument. Try -h for help.")

#        self.global_config = self._read_global_config()

        if self.options.debug:
            os.environ['DEBUG'] = "true"
            
        if self.options.url:
            self._set_server(self.options.url)
            
#    def _read_global_config(self):
#        """
#        Read global candlepin configuration from
#        """
#        config_dir = os.path.join("/etc/candlepin")
#        filename = os.path.join(config_dir, GLOBAL_BUILD_PROPS_FILENAME)
#        if not os.path.exists(filename):
#            # HACK: Try the old filename location, pre-tito rename:
#            oldfilename = os.path.join(config_dir, "global.build.py.props")
#            if not os.path.exists(oldfilename):
#                error_out("Unable to locate branch configuration: %s\nPlease run 'tito init'" %
#                        filename)
#        config = ConfigParser.ConfigParser()
#        config.read(filename)
#
#        # Verify the config contains what we need from it:
#        required_global_config = [
#                (GLOBALCONFIG_SECTION, DEFAULT_BUILDER),
#                (GLOBALCONFIG_SECTION, DEFAULT_TAGGER),
#        ]
#        for section, option in required_global_config:
#            if not config.has_section(section) or not \
#                config.has_option(section, option):
#                    error_out("%s missing required config: %s %s" % (
#                        filename, section, option))
#
#        return config

    def _validate_options(self):
        """
        Subclasses can implement if they need to check for any
        incompatible cmd line options.
        """
        pass

class ListModule(BaseCliModule):
    def __init__(self):
        BaseCliModule.__init__(self)
        
        usage = "usage: %prog list [options]"
        self.parser = OptionParser(usage)
        
        self._add_common_options()
        
    def main(self):
        BaseCliModule.main(self)

        conn = httplib.HTTPConnection(self.server_url)
        headers = {"Content-type":"application/json",
                   "Accept":"application/json"}
        conn.request("GET", '/candlepin/consumer/list', None, headers)
        response = conn.getresponse()
        rsp = response.read()
        conn.close()
        print("Consumers: %s" % rsp)
        
class EntitleModule(BaseCliModule):
    
    def __init__(self):
        BaseCliModule.__init__(self)
        
        usage = "usage: %prog entitle [options]"
        self.parser = OptionParser(usage)
        
        self._add_common_options()
        
        self.parser.add_option("--username", dest="username", help="supply username")
        
    def main(self):
        BaseCliModule.main(self)
        
        # decide what needs to happen based on options
        
        # login
        # create a consumer
        
        print("Creating Consumer")
        params = urllib.urlencode({'name':'client'})
        headers = {"Content-type":"application/json",
                   "Accept":"application/json"}
        conn = httplib.HTTPConnection(self.server_url)
        conn.request("POST", '/candlepin/consumer', params, headers)
        response = conn.getresponse()
        rsp = response.read()
        conn.close()
        print("consumer created: %s" % rsp)
        # ask to be entitled
        
    def _validate_options(self):
        # validate the options here if need be
        pass

CLI_MODULES = {
    "entitle": {"class":EntitleModule, "help":"Entitle a product."},
    "list": {"class":ListModule, "help":"List consumers."}
#    "tag": TagModule,
#    "report": ReportModule,
#    "init": InitModule
}

