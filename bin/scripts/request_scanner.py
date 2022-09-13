#!/usr/bin/env python3

import argparse
import logging
import re
import sys


# Example messages
# 2017-11-20 03:37:20,737 [thread=http-bio-8443-exec-4] [req=8138a38e-1e06-4292-aaa3-e205bbba9dc5, org=, csid=bd832795] INFO  org.candlepin.common.filter.LoggingFilter - Request: verb=GET, uri=/candlepin/consumers/d3a179bb-f5b6-40e7-85bd-4f8a41e3c709
# 2018-01-23 11:59:45,966 [thread=http-nio-8443-exec-4] [req=1a8d9011-d622-42e2-88cd-258e8dd1fe8c, org=, csid=] DEBUG org.candlepin.resteasy.filter.AbstractAuthorizationFilter - Request: verb=GET, uri=/candlepin/status
# 2018-01-23 11:59:45,980 [thread=http-nio-8443-exec-4] [req=1a8d9011-d622-42e2-88cd-258e8dd1fe8c, org=, csid=] DEBUG org.candlepin.resteasy.filter.AbstractAuthorizationFilter - Request: GET /candlepin/status

# 2017-11-20 03:37:20,762 [thread=http-bio-8443-exec-4] [req=8138a38e-1e06-4292-aaa3-e205bbba9dc5, org=Default_Organization, csid=bd832795] INFO  org.candlepin.common.filter.LoggingFilter - Response: status=200, content-type="application/json", time=25
# 2018-01-23 10:53:02,594 [thread=http-nio-8443-exec-4] [req=1b825983-0bbd-47d8-b9c0-b00d16ab1d2c, org=, csid=] DEBUG org.candlepin.common.filter.LoggingFilter - Response: 200 OK (221 ms)

# Request: verb=(\w+), uri=(.+)
# Response: status=(\d+), content-type=(.+?), time=(\d+)


class RegexAction(argparse.Action):
    """
    A simple Action that tries to parse an argument as regexp, and sets
    the regular expression object instead of the string as value in the
    Namespace.
    """

    def __call__(self, parser, namespace, values, option_string=None):
        try:
            regex = re.compile(values)
        except re.error as e:
            msg = (
                f"Invalid regular expression '{values}' for {option_string}: {str(e)}\n"
            )
            parser.exit(status=2, message=msg)
        else:
            setattr(namespace, self.dest, regex)


def process_log(args, fp):

    status_regex = re.compile(
        r"([0-9\- :,]+) \[thread=([^\]]+)\] \[req=([0-9a-z\-]+), org=([^,]*), csid=([^\]]*)\] (INFO|DEBUG) .+ - (?:(Request|Response): (.+))"
    )

    req_regex = re.compile(r"\s*verb=([A-Z]+), uri=(.+)\s*")
    res_regex = re.compile(r"\s*status=(\d+), content-type=.+?, time=(\d+)\s*")
    dbg_res_regex = re.compile(r"\s*(\d+) .+? \((\d+) ms\)\s*")
    double_req_regex = re.compile(r"\s*[A-Z]+ .+")

    req_ids = set()
    requests = {}
    proc_requests = {}
    slow_requests = {}
    failed_requests = {}

    logging.info(
        "Processing file %s using slow request threshold of %d ms",
        args.file,
        args.slow_req_threshold,
    )

    for lineno, line in enumerate(fp, start=1):
        # Output some dots to show the process is still running when processing huge logs
        if lineno % 100000 == 0:
            sys.stdout.write(".")

        sdata = status_regex.match(line)

        if sdata:
            req_ids.add(sdata[3].lower())

            if sdata[7] == "Request":
                request = {
                    "timestamp": sdata[1],
                    "thread": sdata[2],
                    "id": sdata[3],
                    "org": sdata[4],
                    "csid": sdata[5],
                }

                rdata = req_regex.match(sdata[8])

                if rdata:
                    request["verb"] = rdata[1]
                    request["uri"] = rdata[2]

                    if (
                        args.uri_filter is None or args.uri_filter.match(request["uri"])
                    ) and (
                        args.method_filter is None
                        or args.method_filter.match(request["verb"])
                    ):
                        requests[request["id"]] = request
                else:
                    # Ignore doubled request lines in debug mode.
                    if sdata[6] != "DEBUG" or not double_req_regex.search(sdata[8]):
                        logging.error(
                            "Unable to parse request data for request %s: %s",
                            request["id"],
                            sdata[8],
                        )

            elif sdata[7] == "Response":  # Response
                try:
                    request = requests.pop(sdata[3])
                except KeyError:
                    if args.uri_filter is None and args.method_filter is None:
                        logging.warning(
                            "Response found without matching request for ID: %s",
                            sdata[3],
                        )
                else:
                    proc_requests[request["id"]] = request
                    regexp = res_regex
                    if sdata[6] == "DEBUG":
                        regexp = dbg_res_regex
                    rdata = regexp.match(sdata[8])

                    if rdata:
                        request["status"] = rdata[1]
                        request["time"] = rdata[2]

                        if int(request["time"]) >= args.slow_req_threshold:
                            slow_requests[request["id"]] = request

                        if int(request["status"]) < 200 or int(request["status"]) > 299:
                            failed_requests[request["id"]] = request
                    else:
                        logging.error(
                            "Unable to parse response data for request %s: %s",
                            request["id"],
                            sdata[8],
                        )

    if not args.silent:
        print()
    logging.info(
        "Finished processing file. %d lines processed, %d requests processed",
        lineno,
        len(req_ids),
    )

    if not args.req_ids_only:
        if args.show_all and len(proc_requests) > 0:
            logging.info("Request(s) processed:")

            for req_id, request in proc_requests.items():
                print(
                    f"  {request['timestamp']}: {req_id} => {request['verb']} {request['uri']} -- thread: {request['thread']}"
                )

        print()
        if len(requests) > 0:
            logging.warning("Found %d incomplete request(s):", len(requests))

            for req_id, request in requests.items():
                print(
                    f"  {request['timestamp']}: {req_id} => {request['verb']} {request['uri']} -- thread: {request['thread']}"
                )
        else:
            logging.info("Found 0 incomplete requests")

        print()
        if len(slow_requests) > 0:
            logging.warning("Found %d slow request(s):", len(slow_requests))

            for req_id, request in slow_requests.items():
                print(
                    f"  {request['timestamp']}: {req_id} => {request['verb']} {request['uri']} -- time: {request['time']}ms"
                )
        else:
            logging.info("Found 0 slow requests")

        print()
        if len(failed_requests) > 0:
            logging.warning("Found %d failed request(s):", len(failed_requests))

            for req_id, request in failed_requests.items():
                print(
                    f"  {request['timestamp']}: {req_id} => {request['verb']} {request['uri']} -- response: {request['status']}"
                )
        else:
            logging.info("Found 0 failed requests")

    else:
        if args.show_all:
            if len(proc_requests) > 0:
                for req_id in proc_requests.keys():
                    print(req_id)

            if len(requests) > 0:
                for req_id in requests.keys():
                    print(req_id)
        else:
            if len(requests) > 0:
                for req_id in requests.keys():
                    print(req_id)

            if len(slow_requests) > 0:
                for req_id in slow_requests.keys():
                    print(req_id)

            if len(failed_requests) > 0:
                for req_id in failed_requests.keys():
                    print(req_id)


parser = argparse.ArgumentParser()
parser.add_argument(
    "file",
    metavar="FILE",
    nargs="?",
    default="/var/log/candlepin/candlepin.log",
    help="the log file to parse; defaults to %(default)s",
)
parser.add_argument(
    "--srt",
    dest="slow_req_threshold",
    type=int,
    default=5000,
    metavar="TIME",
    help='Threshold to use for flagging a request as "slow," in milliseconds; defaults to %(default)s',
)
parser.add_argument(
    "--uri",
    dest="uri_filter",
    metavar="REGEX",
    action=RegexAction,
    help="Regular expression to filter requests by URI; defaults to none",
)
parser.add_argument(
    "--method",
    dest="method_filter",
    metavar="REGEX",
    action=RegexAction,
    help="Regular expression to filter requests by request method; defaults to none",
)
parser.add_argument(
    "--idsonly",
    dest="req_ids_only",
    action="store_true",
    help="Only display request IDs; defaults to %(default)s",
)
parser.add_argument(
    "--showall",
    dest="show_all",
    action="store_true",
    help="Displays all processed request information; defaults to %(default)s",
)
args = parser.parse_args()
args.silent = args.req_ids_only

log_level = logging.DEBUG
if args.silent:
    log_level = logging.CRITICAL
logging.basicConfig(
    stream=sys.stdout, format="%(levelname)s: %(message)s", level=log_level
)

try:
    fp = open(args.file)
except (FileNotFoundError, PermissionError) as e:
    parser.exit(status=2, message=f"Cannot open {args.file}: {e.strerror}\n")
else:
    process_log(args, fp)
