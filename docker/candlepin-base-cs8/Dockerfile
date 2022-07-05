FROM quay.io/centos/centos:stream8
LABEL author="Chris Rog <crog@redhat.com>"

ENV LANG en_US.UTF-8

COPY /base-scripts/dockerlib.sh /root/
COPY /candlepin-base-cs8/setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Need a wrapper script to get proper start/stop behaviour with supervisord:
COPY /candlepin-base-cs8/setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
COPY /base-scripts/setup-db.sh /root/
COPY cp-test /usr/bin/

# Centos Streams 8 uses Python3 so we must create alias to not break existing scripts.
RUN echo 'alias python="python3"' >> ~/.bashrc

EXPOSE 8443 22

#CMD ["/usr/bin/cp-test", "-t", "-u", "-r"]
CMD ["true"]