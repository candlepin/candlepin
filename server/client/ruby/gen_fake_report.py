#!/bin/python
import uuid
import json
import argparse
import random
import sys


class AbstractVirtWhoReportGenerator(object):

    def __init__(self, num_hypervisors, num_guests):
        self.hypervisors = num_hypervisors
        self.guests = num_guests

    def generate(self):
        # Returns a json representation of the generated report
        return json.dumps(self._generate())

    def gen_hypervisor(self, num_guests, min_guests=0, max_guests=None):
        # Subclasses should implement this method to return a hypervisor with
        # The given number of guests.
        # Min guests, max_guests should allow for variation in the amount of
        # guests for the hypervisor.
        # Should return a dictionary representation of the guest
        raise NotImplemented()

    def gen_guest(self):
        # Generate a guest. Should return a dictionary representation of the
        # guest
        return {
            "guestId": str(uuid.uuid4()),
            "state": random.choice(range(1, 6))
        }


class VirtWhoReportGenerator(AbstractVirtWhoReportGenerator):

    def _generate(self):
        report = {}
        for x in range(0, self.hypervisors):
            report[str(uuid.uuid4())] = [self.gen_guest() for y in
                range(0, self.guests)]
        return report


class AsyncVirtWhoReportGenerator(AbstractVirtWhoReportGenerator):

    def gen_hypervisor(self, num_guests, min_guests=0, max_guests=None):
        return {"hypervisorId": {"hypervisorId": str(uuid.uuid4())},
                "guestIds": [self.gen_guest() for x in range(0, num_guests)]}

    def _generate(self):
        report = {'hypervisors': [self.gen_hypervisor(self.guests) for x in
                                  range(0, self.hypervisors)]}
        return report


def init_parser():
    parser = argparse.ArgumentParser(description="Generate a fake report to be sent by virt-who")
    parser.add_argument('--hypervisors', metavar='N', type=int,
                        help="The number of hypervisors to include.",
                        action="store", dest="num_hypervisors")
    parser.add_argument('--guests', metavar="N", type=int, dest="num_guests", default=-1)
    parser.add_argument('--reports', metavar="N", type=int, dest="num_reports", default=1)
    parser.add_argument('--format', type=str, dest="format", help="The format of report to generate ('sync' or 'async')")
    parser.add_argument("-o", nargs="?", type=argparse.FileType('w'),
                        default=sys.stdout, dest='outfile')
    args = parser.parse_args()

    args.format = str.lower(args.format).strip()

    return args


def get_generator(format='sync'):
    format_to_generator_map = {
        'sync': VirtWhoReportGenerator,
        'async': AsyncVirtWhoReportGenerator
    }
    return format_to_generator_map[format]


def init_generator(args):
    return get_generator(args.format)(args.num_hypervisors, args.num_guests)


if __name__ == '__main__':
    args = init_parser()

    generator_class = get_generator(format=args.format)
    generator = generator_class(args.num_hypervisors, args.num_guests)
    for x in range(args.num_reports):
        report = generator.generate()
        args.outfile.write(report + '\n')
