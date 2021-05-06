# configure-artemis.py

This script will install Apache Artemis as a service. Its intent is to
provide an easy way to prepare a remote broker that candlepin can be
configured to connect to.

To install artemis, this script will:
* download the specified version of Artemis
* install the broker according to recommendations from the Artemis docs
* create a service runnable by systemctl
* set up the appropriate SELinux policy for the service.
* configure the broker according to candlepin requirements.
* generate certificates and configure the broker to use SSL.
* provide a means to clean up an existing installation of the broker.


The core Artemis files are extracted to /opt/apache-artemis-${VERSION}.

The candlepin broker instance is installed to /var/lib/artemis/candlepin

The artemis service can be started via:

```bash
# systemctl start artemis
# systemctl stop artemis
```

The Artemis installation can be cleaned up by running:
```bash
# ./configure-artemis.py --clean
```

The artemis service can be automatically started after installation if the
start option is specifed. By default, the service is not started.
```bash
# ./configure-artemis --start
```

If deploying for a development installation of candlepin, running the script
with no arguments will suffice. The script does allow overriding a few default
configuration settings, though in most cases this will not be required.

Options:

version: Allows you to install a specific version of the artemis broker.
broker-config: Allows you to specify any broker config template.


