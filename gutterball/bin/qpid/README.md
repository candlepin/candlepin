Install qpid:

    sudo yum install qpid-cpp-server qpid-tools
    sudo yum install qpid-cpp-server-linearstore || sudo yum install qpid-cpp-server-store

QPid requires a package to be installed to persist items to disk.  The old
implementation was "qpid-cpp-server-store" and the newer one is
"qpid-cpp-server-linearstore".  You need one of them or else everything vanishes
once the service is restarted.

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
