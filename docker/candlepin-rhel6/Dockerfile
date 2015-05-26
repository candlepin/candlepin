# Latest brew candlepin RPMs on RHEL 6
FROM docker-registry.usersys.redhat.com/brew/rhel6:5-7
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

# Add internal RHEL repo:
ADD rhel.repo /etc/yum.repos.d/
RUN rpm -ivh http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
# Postgresql binary is needed for some cpsetup commands, even though we
# do not run a server in this container:
RUN yum install -y vim-enhanced python-pip postgresql postgresql-jdbc

ADD candlepin.repo /etc/yum.repos.d/
RUN yum install -y candlepin candlepin-tomcat6

ADD setup-supervisor.sh /root/
RUN /bin/bash /root/setup-supervisor.sh

# TODO: Temporary until I get correct code into a build:
ADD cpdb /root/cpdb
ADD cpsetup /root/cpsetup

EXPOSE 8443

ADD startup.sh /root/startup.sh

RUN rpm -qa | grep candlepin

CMD ["/bin/bash", "/root/startup.sh"]
