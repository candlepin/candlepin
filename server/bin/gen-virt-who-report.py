#!/usr/bin/env python
import json
import random
import string

checkin = {}
for i in range(150):
    guest_ids = []
    for j in range(15):
        guest_ids.append(''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(32)))
    checkin['hypervisor' + ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(22))] = guest_ids

with open('/home/dgoodwin/src/candlepin/server/virtperf/checkin.json', 'w') as outfile:
    json.dump(checkin, outfile, indent=4, ensure_ascii=False)
