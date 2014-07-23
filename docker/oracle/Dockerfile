FROM docker.usersys.redhat.com/dgoodwin/candlepin-base
MAINTAINER Devan Goodwin <dgoodwin@redhat.com>

# Configure oracle:
# TODO: move this to script later:

# This is a bit slow and expensive so we'll do it as a separate
# layer so the setup script can be tweaked quicker:
RUN wget -r --no-parent -l 1 http://file.rdu.redhat.com/~dgoodwin/oracle/ -nH -nd -P /root/oracle/

ADD setup-oracle.sh /root/
ADD xe.rsp /root/
ADD launch-oracle.sh /usr/bin/
ADD init.ora /root/
ADD initXETemp.ora /root/

RUN /bin/bash /root/setup-oracle.sh

CMD ["/usr/bin/cp-test", "-r -o"]
