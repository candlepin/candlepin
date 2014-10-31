Install qpid:

    sudo yum install qpid-cpp-server-store qpid-cpp-server qpid-tools

Now configure qpid to work with SSL:

    ./configure-qpid.sh

The script tries to be as accommodating as possible when it comes to not
destroying previously set-up certificates.  However, if you run the script and
have major issues, the best bet is to simply wipe everything and start anew.
Here's how:

1. `systemctl stop qpidd tomcat`
2. `rm /etc/{candlepin,gutterball}/certs/amqp/*`
3. `rm /etc/qpid/brokerdb/*`
4. `rm keys/*` if the `keys` directory is already present from a previous run
   of the script.
