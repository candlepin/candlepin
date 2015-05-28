# Latest brew candlepin RPMs on RHEL 6
FROM candlepin/candlepin-preinstall-rhel6
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

ADD candlepin.repo /etc/yum.repos.d/
RUN yum install -y candlepin

ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "/root/startup.sh"]
