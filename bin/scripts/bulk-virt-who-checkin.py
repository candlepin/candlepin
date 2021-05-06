#!/usr/bin/env python

from rhsm.connection import UEPConnection
import json

cp = UEPConnection(
    host='lenovo.local.rm-rf.ca',
    ssl_port=8443,
    handler='/candlepin',
    username='admin',
    password='admin')

#cp = UEPConnection(
#    host='subscription.rhn.redhat.com',
#    ssl_port=443,
#    cert_file='/etc/pki/consumer/cert.pem',
#    key_file='/etc/pki/consumer/key.pem')

mapping = json.loads(open('/home/dgoodwin/src/candlepin/server/virtperf/checkin.json').read())
cp.hypervisorCheckIn('virtperf', None, mapping)
#cp.hypervisorCheckIn('5894300', None, mapping)
