#!/bin/python
import uuid
import json
import argparse
import random
import sys
import string


class AbstractVirtWhoReportGenerator(object):

    def __init__(
            self, num_hypervisors, num_guests, virt_type, hypervisor_type,
            hypervisor_id_format):
        self.hypervisors = num_hypervisors
        self.guests = num_guests
        self.virt_type = virt_type
        self.hypervisor_type = hypervisor_type
        self.hypervisor_id_format = hypervisor_id_format

    def generate(self):
        """
        Returns a json representation of the generated report
        """
        return json.dumps(self._generate())

    def gen_hypervisor(self, num_guests, min_guests=0, max_guests=None):
        """
        Subclasses should implement this method to return a hypervisor with
        The given number of guests. Min guests, max_guests should allow for
        variation in the amount of guests for the hypervisor. Should return
        a dictionary representation of the guest
        """
        raise NotImplemented()

    def gen_guest(self):
        """
        Generate a guest. Should return a dictionary representation of the
        guest.
        """
        return {
            "guestId": str(uuid.uuid4()),
            "state": random.choice(range(1, 6)),
            "attributes": {
                "virtWhoType": self.virt_type,
                "active": random.choice(range(0, 2))
            }
        }

    def gen_facts(self, hypervisor_uuid=str(uuid.uuid4())):
        """
        Generate guests facts.
        """
        return {
            "cpu.cpu_socket(s)": random.choice(range(1, 6)),
            "hypervisor.type": self.hypervisor_type,
            "dmi.system.uuid": hypervisor_uuid
        }


class VirtWhoReportGenerator(AbstractVirtWhoReportGenerator):

    def _generate(self):
        report = {}
        for x in range(0, self.hypervisors):
            report[str(uuid.uuid4())] = [
                self.gen_guest() for y in range(0, self.guests)
            ]
        return report


class AsyncVirtWhoReportGenerator(AbstractVirtWhoReportGenerator):

    def gen_hypervisor(self, num_guests, min_guests=0, max_guests=None):
        if self.hypervisor_id_format == 'uuid':
            hypervisor_uuid = str(uuid.uuid4())

            return {"hypervisorId": {"hypervisorId": hypervisor_uuid},
                    "name": "generated_hypervisor-" + gen_random_string(),
                    "guestIds": [
                        self.gen_guest() for x in range(0, num_guests)],
                    "facts": self.gen_facts(hypervisor_uuid)}
        else:
            hypervisor_name = "generated_hypervisor-" + gen_random_string()

            return {"hypervisorId": {"hypervisorId": hypervisor_name},
                    "name": hypervisor_name,
                    "guestIds": [
                        self.gen_guest() for x in range(0, num_guests)],
                    "facts": self.gen_facts()}

    def _generate(self):
        report = {'hypervisors': [self.gen_hypervisor(self.guests) for x in
                                  range(0, self.hypervisors)]}
        return report


def init_parser():
    parser = argparse.ArgumentParser(
        description="Generate a fake report to be sent by virt-who"
    )
    parser.add_argument('--hypervisors', metavar='N', type=int,
                        help="The number of hypervisors to include.",
                        action="store", dest="num_hypervisors")
    parser.add_argument('--guests', metavar="N", type=int, dest="num_guests",
                        default=1)
    parser.add_argument('--v', metavar="virtWhoType", type=str,
                        help="Virt-who type (Default - libvirt)",
                        dest="virt_type", default="libvirt")
    parser.add_argument('--h', metavar="hypervisorType", type=str,
                        help="Hypervisor type (Default - QEMU)",
                        dest="hypervisor_type", default="QEMU")
    parser.add_argument('--id', type=str, dest="hypervisor_id_format",
                        choices=['uuid', 'hostname'], help="Hypervisor ID " +
                        "format 'uuid' or 'hostname' (Default - uuid)",
                        default="uuid")
    parser.add_argument('--reports', metavar="N", type=int,
                        dest="num_reports", default=1)
    parser.add_argument('--format', type=str, dest="format",
                        choices=['async', 'sync'], help="The format of " +
                        "report to generate ('sync' or 'async')")
    parser.add_argument("-o", nargs="?", type=argparse.FileType('w'),
                        default=sys.stdout, dest='outfile')
    args = parser.parse_args()

    args.format = str.lower(args.format).strip()
    args.hypervisor_id_format = str.lower(args.hypervisor_id_format).strip()

    return args


def gen_random_string():
    """
    Generates random alphanumeric strings
    """
    return ''.join(random.choice(
        string.ascii_lowercase + string.digits) for i in range(6))


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
    generator = generator_class(
        args.num_hypervisors, args.num_guests, args.virt_type,
        args.hypervisor_type, args.hypervisor_id_format
    )
    for x in range(args.num_reports):
        report = generator.generate()
        args.outfile.write(report + '\n')
