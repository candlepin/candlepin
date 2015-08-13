FROM candlepin/candlepin-base
MAINTAINER Chris Rog <crog@redhat.com>

ADD setup-mysql.sh /root/

RUN /bin/bash /root/setup-mysql.sh

CMD ["/usr/bin/cp-test", "-t", "-u", "-r"]
