#!/bin/bash

cleanup_env() {
  /usr/bin/yum clean all
  /usr/bin/find /var/log/ -type f -exec /usr/bin/cp /dev/null {} \;
}
