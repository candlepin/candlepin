FROM temp_base_candlepin:latest

MAINTAINER Nikos Moumoulidis <nmoumoul@redhat.com>

COPY setup-supervisor.sh /root/
RUN /bin/bash /root/setup-supervisor.sh

COPY setup-env-extras.sh /root/
RUN /bin/bash -l /root/setup-env-extras.sh

ADD startup.sh /root/startup.sh
CMD ["/bin/bash", "-l", "/root/startup.sh"]
