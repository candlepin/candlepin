FROM centos:7
MAINTAINER Nikos Moumoulidis <nmoumoul@redhat.com>

ENV LANG en_US.UTF-8

COPY setup-env.sh /root/
RUN /bin/bash /root/setup-env.sh

EXPOSE 8443 22
