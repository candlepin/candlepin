FROM quay.io/centos/centos:stream9
LABEL author="Štěpán Bakaj <sbakaj@redhat.com>"

# This is a fix to correctly set language and to avoid warning: Failed to set locale, defaulting to C.UTF-8
RUN dnf install -y glibc-langpack-en
ENV LANG en_US.UTF-8

# gai.conf is needed to avoid timeouts when accessing rubygems
# https://help.rubygems.org/discussions/problems/31074-timeout-error
COPY /gai.conf /etc/gai.conf
COPY /base-scripts/dockerlib.sh /root/
COPY /candlepin-base-cs9/setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Need a wrapper script to get proper start/stop behaviour with supervisord:
COPY /candlepin-base-cs9/setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
COPY /base-scripts/setup-db.sh /root/
COPY cp-test /usr/bin/

EXPOSE 8443 22

#CMD ["/usr/bin/cp-test", "-t", "-u", "-r"]
CMD ["true"]
