#! /usr/bin/python
from __future__ import print_function, unicode_literals

import sys
import argparse
import logging
import proton
import proton.handlers
import proton.reactor
import proton.utils

logging.basicConfig()
log = logging.getLogger("qpid_watch")


class TimeoutError(Exception):
    pass


class QpidWatcher(proton.handlers.MessagingHandler):
    def __init__(self, args):
        super(QpidWatcher, self).__init__()

        if args.ssl_cert and args.ssl_key:
            self.ssl_domain = proton.SSLDomain(proton.SSLDomain.MODE_CLIENT)
            self.ssl_domain.set_credentials(args.ssl_cert, args.ssl_key, None)
            # Bad practice, but this is meant for dev testing only.
            self.ssl_domain.set_peer_authentication(proton.SSLDomain.ANONYMOUS_PEER)
        else:
            self.ssl_domain = None

        self.received = 0
        self.expected = args.messages
        self.url = args.address
        self.source = args.source
        self.timeout = args.timeout
        self.subject = args.subject
        self.show_message = args.show_message

    def on_start(self, event):
        conn = event.container.connect(url=self.url, ssl_domain=self.ssl_domain)

        if self.timeout > 0:
            self.last_event = event.container.mark()
            event.container.schedule(self.timeout, self)

        # Note that Copy() does not actually consume the message off the queue!
        self.receiver = event.container.create_receiver(conn, self.source, options=proton.reactor.Copy())

    def on_timer_task(self, event):
        elapsed = event.container.mark() - self.last_event
        # elapsed is in milliseconds
        if elapsed > self.timeout * 1000:
            raise TimeoutError("No activity for %d seconds" % self.timeout)
        else:
            # Reschedule for the next timeout period
            event.container.schedule(self.timeout, self)

    def on_message(self, event):
        self.last_event = event.container.mark()
        if event.message.id and event.message.id < self.received:
            # ignore duplicate message
            return
        if self.expected == -1 or self.received < self.expected:
            # If a subject to filter on is given and the message matches or if no filter is present
            if (self.subject and event.message.subject in self.subject) or not self.subject:
                self.received += 1
                log.debug(event.message)
                if self.show_message:
                    print(event.message.body)

            if self.received == self.expected:
                log.debug("Closing connection")
                event.receiver.close()
                event.connection.close()
                event.container.stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser("Watch a Qpid queue for a message")
    parser.add_argument("-c", "--ssl-cert", help="Certificate to use to connect to Qpid over SSL")
    parser.add_argument("-k", "--ssl-key", help="Key to use to connect to Qpid over SSL")
    parser.add_argument("-a", "--address", help="connection string to use (defaults to %(default)s)",
        default="amqps://localhost:5671")
    parser.add_argument("-s", "--source", help="Qpid source to listen to (defaults to '%(default)s')",
        default="event")
    parser.add_argument("-t", "--timeout", help="Seconds to wait before timeout; -1 waits indefinitely"
        " (defaults to %(default)d s)", type=int, default=30)
    parser.add_argument("-m", "--messages", help="number of messages to receive; -1 receives indefinitely"
        " (defaults to %(default)d)", type=int, default=-1)
    parser.add_argument("--subject", help="subject to watch for", action="append")
    parser.add_argument("--show-message", help="show the message received", action="store_true",
        default=False)

    group = parser.add_mutually_exclusive_group()
    group.add_argument("-v", "--verbose", help="verbose mode", action="store_true")
    group.add_argument("-q", "--quiet", help="quiet mode", action="store_true")

    args = parser.parse_args()

    # XOR the cert and key arguments.  We want either both or none at all.
    if bool(args.ssl_cert) ^ bool(args.ssl_key):
        parser.exit("You must specify both the SSL certificate and key if you are going to use SSL")

    if not (args.messages > 0 or args.messages == -1):
        parser.exit("--messages must either be a positive integer or -1")

    if args.verbose:
        log.setLevel(logging.DEBUG)
    else:
        log.setLevel(logging.INFO)

    log.debug(args)
    qpid_watcher = QpidWatcher(args)

    try:
        rc = 0
        reactor = proton.reactor.Container(qpid_watcher)
        if args.timeout > 0:
            reactor.timeout = args.timeout
        reactor.run()
    except KeyboardInterrupt:
        # Throw an error if the user KeyboardInterrupts before the number of messages to receive has been hit
        rc = 1
    except TimeoutError as e:
        rc = 2
        log.debug(e, exc_info=e)
        print("Connection timed out")
    except Exception as e:
        rc = 3
        log.exception("Unknown error")
    finally:
        if not args.quiet:
            output = "%d messages received" % qpid_watcher.received
            if args.messages > 0:
                output += " (%d expected)" % args.messages
            print(output)
        sys.exit(rc)

