# Latest brew candlepin RPMs on RHEL 6
FROM candlepin/candlepin-rhel6-base
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

ADD candlepin.repo /etc/yum.repos.d/

# https://bugzilla.redhat.com/show_bug.cgi?id=1205054
RUN yum downgrade -y glibc glibc-common gdbm

RUN yum install -y candlepin

ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "/root/startup.sh"]
