FROM fedora:20
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

ENV LANG en_US.UTF-8

ADD setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Need a wrapper script to get proper start/stop behaviour with supervisord:
ADD tomcat-wrapper /usr/bin/tomcat-wrapper
ADD setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
ADD cp-test /usr/bin/

CMD ["/usr/bin/cp-test", "-u"]
