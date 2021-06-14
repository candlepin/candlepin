#!/usr/bin/env python
from __future__ import print_function, division, absolute_import

"""
This Python module contains code related to logging in candlepin deploy scripts
"""

import logging
import sys


def build_logger(name, msg_format=None):
    """Builds and configures our logger"""
    class EmptyLineFilter(logging.Filter):
        """Logging filter implementation that filters on empty or whitespace-only lines"""
        def __init__(self, invert=False):
            self.invert = invert

        def filter(self, record):
            result = bool(record.msg) and not record.msg.isspace()
            if self.invert:
                result = not result

            return result

    if msg_format is None:
        msg_format = '%(asctime)-15s %(levelname)-7s %(name)s -- %(message)s'

    # Create the base/standard handler
    std_handler = logging.StreamHandler(sys.stdout)
    std_handler.setFormatter(logging.Formatter(fmt=msg_format))
    std_handler.addFilter(EmptyLineFilter())

    # Create an empty-line handler
    empty_handler = logging.StreamHandler(sys.stdout)
    empty_handler.setFormatter(logging.Formatter(fmt=''))
    empty_handler.addFilter(EmptyLineFilter(True))

    # Create a logger using the above handlers
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    logger.addHandler(std_handler)
    logger.addHandler(empty_handler)

    return logger
