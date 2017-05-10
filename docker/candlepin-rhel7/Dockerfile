# Latest brew candlepin RPMs on RHEL 7
FROM registry.access.redhat.com/rhel7:latest
MAINTAINER Chris Rog <crog@redhat.com>

# Remove the probably broken rhel repos already in image:
RUN rm -f /etc/yum.repos.d/*.repo

# Add internal RHEL repo:
ADD rhel.repo /etc/yum.repos.d/rhel.repo
RUN rpm -ivh https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm

# Postgresql binary is needed for some cpsetup commands, even though we
# do not run a server in this container:
RUN yum install -y findutils vim-enhanced python-pip postgresql postgresql-jdbc openssl&& \
    yum clean all && \
    /usr/bin/find /var/log/ -type f -exec /usr/bin/cp /dev/null {} \;

ADD setup-supervisor.sh /root/
RUN /bin/bash /root/setup-supervisor.sh

ADD candlepin.repo /etc/yum.repos.d/
RUN yum install -y candlepin candlepin-tomcat && \
    yum clean all && \
    /usr/bin/find /var/log/ -type f -exec /usr/bin/cp /dev/null {} \;

EXPOSE 8443
ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "/root/startup.sh"]
