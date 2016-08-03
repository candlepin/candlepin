# Latest brew candlepin RPMs on RHEL 7
FROM docker-registry.usersys.redhat.com/brew/rhel7:7.0-19-GA
#MAINTAINER Devan Goodwin <dgoodwin@redhat.com>
MAINTAINER Chris Rog <crog@redhat.com>

# Add internal RHEL repo:
ADD rhel.repo /etc/yum.repos.d/
RUN rpm -ivh https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm

# Postgresql binary is needed for some cpsetup commands, even though we
# do not run a server in this container:
RUN yum install -y vim-enhanced python-pip postgresql postgresql-jdbc openssl
RUN rpm -e --nodeps fakesystemd

ADD setup-supervisor.sh /root/
RUN /bin/bash /root/setup-supervisor.sh

EXPOSE 8443

ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "/root/startup.sh"]
