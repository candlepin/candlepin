#!/usr/bin/python

import candlepinapi
import sys
import optparse
import os
import sys
from optparse import OptionParser
from getpass import getpass

class CliCommand(object):
    """ Base class for all sub-commands. """
    def __init__(self, name="cli", usage=None, shortdesc=None,
            description=None):
        self.shortdesc = shortdesc
        if shortdesc is not None and description is None:
            description = shortdesc
        self.parser = OptionParser(usage=usage, description=description)
        self._add_common_options()
        self.name = name

    def _add_common_options(self):
        """ Add options that apply to all sub-commands. """

#        self.parser.add_option("--log", dest="log_file", metavar="FILENAME",
#                help="log file name (will be overwritten)")
#        self.parser.add_option("--log-level", dest="log_level",
#                default="critical", metavar="LEVEL",
#                help=_("log level (debug/info/warning/error/critical)"))

    def _do_command(self):
        pass

    def main(self):

        (self.options, self.args) = self.parser.parse_args()
        # we dont need argv[0] in this list...
        self.args = self.args[1:]

        # do the work, catch most common errors here:
        self._do_command()


class RegisterCommand(CliCommand):
    def __init__(self):
        usage = "usage: %prog register"
        shortdesc = "register"
        desc = "register"

        CliCommand.__init__(self, "register", usage, shortdesc, desc)

        self.username = None
        self.password = None
        self.system = None
        self.parser.add_option("--username", dest="username", 
                               help="username")
        self.parser.add_option("--password", dest="password",
                               help="password")
        self.parser.add_option("--system", dest="system",
                               help="system")

    def _validate_options(self):
        CliCommand._validate_options(self)

    def _do_command(self):
        """
        Executes the command.
        """
        cp = candlepinapi.CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=None)
        print cp.registerConsumer(self.options.username, self.options.password, self.options.system, {}, {})


class UnRegisterCommand(CliCommand):
    def __init__(self):
        usage = "usage: %prog unregister"
        shortdesc = "unregister"
        desc = "unregister"
        CliCommand.__init__(self, "unregister", usage, shortdesc, desc)

        self.username = None
        self.password = None
        self.system = None
        self.parser.add_option("--username", dest="username", 
                               help="username")
        self.parser.add_option("--password", dest="password",
                               help="password")
        self.parser.add_option("--consumer", dest="consumer",
                               help="system")

    def _validate_options(self):
        CliCommand._validate_options(self)

    def _do_command(self):
        """
        Executes the command.
        """
        cp = candlepinapi.CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=None)

        # FIXME: should probably at least pretend to find some hardwrae here
        #print cp.registerConsumer(self.options.username, self.options.password, self.options.system, {}, {})
        print cp.unRegisterConsumer(self.options.username, self.options.password, self.options.consumer)



class BindCommand(CliCommand):
    def __init__(self):
        usage = "usage: %prog bind --consumer [consumer_uuid] --product [product_label] --regtoken [regtoken]"
        shortdesc = "bind"
        desc = "bind"
        CliCommand.__init__(self, "bind", usage, shortdesc, desc)

        self.consumer = None
        self.product = None
        self.regtoken = None
        self.parser.add_option("--consumer", dest="consumer", 
                               help="consumer")
        self.parser.add_option("--product", dest="product",
                               help="product")
        self.parser.add_option("--regtoken", dest="regtoken",
                               help="regtoken")

    def _validate_options(self):
        if self.options.regtoken and self.options.product:
            print "Need --consumer and either --product or --regtoken, not both"
            sys.exit()

        CliCommand._validate_options(self)

    def _do_command(self):
        """
        Executes the command.
        """
        cp = candlepinapi.CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=None)

        # FIXME: should probably at least pretend to find some hardwrae here
        #print cp.registerConsumer(self.options.username, self.options.password, self.options.system, {}, {})
        if self.options.product:
            print cp.bindProduct(self.options.consumer, self.options.product)

        if self.options.regtoken:
            print cp.bindRegToken(self.options.consumer, self.options.regtoken)


class SyncCertificatesCommand(CliCommand):
    def __init__(self):
        usage = "usage: %prog syncCertificates --consumer [consumer_uuid]"
        shortdesc = "syncCertificates"
        desc = "syncCertificates"
        CliCommand.__init__(self, "syncCertificates", usage, shortdesc, desc)

        self.consumer = None
        self.parser.add_option("--consumer", dest="consumer", 
                               help="consumer")

    def _validate_options(self):
        CliCommand._validate_options(self)

    def _do_command(self):
        """
        Executes the command.
        """
        cp = candlepinapi.CandlePinApi(hostname="localhost", port="8080", api_url="/candlepin", debug=None)

        print cp.syncCertificates(self.options.consumer, [])


# taken wholseale from rho...
class CLI:
    def __init__(self):
        self.cli_commands = {}
        for clazz in [RegisterCommand, UnRegisterCommand, BindCommand, SyncCertificatesCommand]:
                cmd = clazz()
                # ignore the base class
                if cmd.name != "cli":
                    self.cli_commands[cmd.name] = cmd 

    def _add_command(self, cmd):
        self.cli_commands[cmd.name] = cmd
        
    def _usage(self):
        print "\nUsage: %s [options] MODULENAME --help\n" % os.path.basename(sys.argv[0])
        print "Supported modules:\n"

        # want the output sorted
        items = self.cli_commands.items()
        items.sort()
        for (name, cmd) in items:
            print("\t%-14s %-25s" % (name, cmd.shortdesc))
        print("")

    def _find_best_match(self, args):
        """
        Returns the subcommand class that best matches the subcommand specified
        in the argument list. For example, if you have two commands that start
        with auth, 'auth show' and 'auth'. Passing in auth show will match
        'auth show' not auth. If there is no 'auth show', it tries to find
        'auth'.

        This function ignores the arguments which begin with --
        """
        possiblecmd = []
        for arg in args[1:]:
            if not arg.startswith("-"):
                possiblecmd.append(arg)

        if not possiblecmd:
            return None

        cmd = None
        key = " ".join(possiblecmd)
        if self.cli_commands.has_key(" ".join(possiblecmd)):
            cmd = self.cli_commands[key]

        i = -1
        while cmd == None:
            key = " ".join(possiblecmd[:i])
            if key is None or key == "":
                break

            if self.cli_commands.has_key(key):
                cmd = self.cli_commands[key]
            i -= 1

        return cmd

    def main(self):
        if len(sys.argv) < 2 or not self._find_best_match(sys.argv):
            self._usage()
            sys.exit(1)

        cmd = self._find_best_match(sys.argv)
        if not cmd:
            self._usage()
            sys.exit(1)

        cmd.main()


if __name__ == "__main__":
    CLI().main()
