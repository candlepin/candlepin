Install qpid:

    sudo yum install qpid-cpp-server-store qpid-cpp-server qpid-tools

Now configure qpid to work with SSL:

    ./configure-qpid.sh

Now add the exchange for events to be read from:

    qpid-config add exchange topic event --durable
