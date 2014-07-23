FROM docker.usersys.redhat.com/dgoodwin/candlepin-base
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

# Configure postgresql:
ADD postgresql-setup /root/
ADD setup-postgresql.sh /root/
RUN /bin/bash /root/setup-postgresql.sh

CMD ["/usr/bin/cp-test", "-r"]
