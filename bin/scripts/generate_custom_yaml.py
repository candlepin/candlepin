#!/usr/bin/env python

import yaml
import sys

data={
    "candlepin.conf": {
        "auth_cloud_enable": "true",
    }
}

#accept the project directory file path
project_dir=sys.argv[1]

custom_file="".join((project_dir,'/custom.yaml'))
f=open(custom_file,'w')
f.write(yaml.dump(data))
f.close()
