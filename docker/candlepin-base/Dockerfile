FROM centos:7
#MAINTAINER Devan Goodwin <dgoodwin@redhat.com>
MAINTAINER Chris Rog <crog@redhat.com>

ENV LANG en_US.UTF-8

COPY dockerlib.sh /root/
COPY setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Need a wrapper script to get proper start/stop behaviour with supervisord:
COPY setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
COPY setup-db.sh /root/
COPY cp-test /usr/bin/

EXPOSE 8443 22

#CMD ["/usr/bin/cp-test", "-t", "-u", "-r"]
CMD ["true"]
