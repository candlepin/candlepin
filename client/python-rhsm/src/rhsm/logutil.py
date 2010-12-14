#
# Copyright (C) 2005-2008 Red Hat, Inc.
# All rights reserved.
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation version 2 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
# Jeff Ortel (jortel@redhat.com)
#

import os
import logging
import string
import traceback

from logging import Formatter
from logging.handlers import RotatingFileHandler

def trace_me():
    x = traceback.extract_stack()
    bar = string.join(traceback.format_list(x))
    return bar
    

def trace_me_more():
    frames = traceback.extract_stack()
    stack = "\n"
    for frame in frames:
        stack = stack + "%s:%s\n" % (os.path.basename(frame[0]), frame[2])
    stack = stack + "\n"
    return stack


def getLogger(name):
    path = '/var/log/rhsm/rhsm.log'
    if not os.path.isdir("/var/log/rhsm"):
        os.mkdir("/var/log/rhsm")
    fmt = '%(asctime)s [%(levelname)s] %(funcName)s() @%(filename)s:%(lineno)d - %(message)s'

    log = logging.getLogger(name)

    # Try to write to /var/log, fallback on console logging:
    try:
        handler = RotatingFileHandler(path, maxBytes=0x100000, backupCount=5)
    except IOError, e:
        handler = logging.StreamHandler()

    handler.setFormatter(Formatter(fmt))
    log.addHandler(handler)
    log.setLevel(logging.DEBUG)
    return log
    
    
