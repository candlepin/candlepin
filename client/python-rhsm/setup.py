#!/usr/bin/env python
#
# Copyright (c) 2008-2009 Red Hat, Inc.
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

from setuptools import setup, find_packages


setup(
    name="rhsm",
    version='0.94.3',
    description='A Python library to communicate with a Red Hat Unified Entitlement Platform',
    author='Devan Goodwin',
    author_email='dgoodwin@redhat.com',
    url='http://fedorahosted.org/candlepin',
    license='GPLv2',

    package_dir={
        'rhsm': 'src/rhsm',
    },
    packages = find_packages('src'),
    include_package_data = True,

    classifiers = [
        'License :: OSI Approved :: GNU General Public License (GPL)',
        'Intended Audience :: Developers',
        'Intended Audience :: Information Technology',
        'Programming Language :: Python'
    ],
)


