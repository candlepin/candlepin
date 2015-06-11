# Latest brew candlepin RPMs on RHEL 7
FROM candlepin/candlepin-rhel7-base
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

ADD candlepin.repo /etc/yum.repos.d/
RUN yum install -y candlepin candlepin-tomcat

ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "/root/startup.sh"]
