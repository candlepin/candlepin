# Latest brew candlepin RPMs on RHEL 6
FROM docker-registry.usersys.redhat.com/brew/rhel6:6-6
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

# Remove the probably broken rhel repos already in image:
#RUN rm -f /etc/yum.repos.d/*.repo

# Add internal RHEL repo:
ADD rhel.repo /etc/yum.repos.d/
RUN rpm -ivh http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm

# Postgresql binary is needed for some cpsetup commands, even though we
# do not run a server in this container:
RUN yum install -y python-pip postgresql postgresql-jdbc

ADD setup-supervisor.sh /root/
RUN /bin/bash /root/setup-supervisor.sh

EXPOSE 8443

ADD startup.sh /root/startup.sh

CMD ["/bin/bash", "/root/startup.sh"]
