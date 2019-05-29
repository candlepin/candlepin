FROM temp_base_candlepin:latest

MAINTAINER Nikos Moumoulidis <nmoumoul@redhat.com>

# Need a wrapper script to get proper start/stop behaviour with supervisord:
COPY setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for deploying candlepin, loading and dumping test data.
COPY cp-test /usr/bin/

CMD ["/bin/bash", "-l", "/usr/bin/cp-test", "-g"]
