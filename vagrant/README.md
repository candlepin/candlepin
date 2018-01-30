# Testing Candlepin with Vagrant
The vagrant test setup performs the following actions:
* Deploy a Centos 7 vm
* NFS mount your local source directory into the VM
* Configure Tomcat remote debugging
* Install Postgres and configure the candlepin user
  without an external messaging broker).
* Compile & install Candlepin
* Forward Ports to your local system for debugging
  * Tomcat remote debug on port 8000
  * REST access for Candlepin on port 8080 and 8443
* Optionally, setup yourkit profiler to enable profiling of the vm

## Prerequisites
Running the Candlepin Vagrant deployer needs an up to date version of Vagrant
and Ansible. If the vagrant-hostmanager pluign is installed then the hostname
for the development candlepin box will automatically be added to your hosts file.

The NFS service requires +x attribute on all parent directories in order to mount
the source directory. If you are running into problems with the NFS mount this would
be the first thing to check.  

Make sure NFS is enabled on firewall using:

    $ firewall-cmd --list-all

To enable NFS on firewall for vagrant network (192.168.121.0/24 in this case) you will
have to type following commands (Fedora/RHEL7) on host:

    $ firewall-cmd --permanent --add-rich-rule='rule family="ipv4" \
      source address="192.168.121.0/24" service name="rpc-bind" accept'
    $ firewall-cmd --permanent --add-rich-rule='rule family="ipv4" \
      source address="192.168.121.0/24" service name="nfs" accept'
    $ firewall-cmd --permanent --add-rich-rule='rule family="ipv4" \
      source address="192.168.121.0/24" service name="mountd" accept'

If you have other services running on ports 8000, 8080, or 8443 they will either have
to be stopped or you will have to edit the Vagrantfile and choose different ports.

## Getting Started
1. From the root directory of your Candlepin checkout run `vagrant up`
1. When Vagrant up finishes you should be able to check the status of the server
   by running `curl -k -u admin:admin "http://localhost:8080/candlepin/status"`

## Directly connecting to the database using psql
1. If it has not already been started `vagrant up` the candlepin system.
1. From the root directory of your Candlepin checkout run `vagrant ssh` to connect to the system.
1. `psql -U candlepin candlepin`

## Recompile & redeploy
1. From the root directory of your Candlepin checkout run `vagrant up`
1. Run `vagrant ssh` to connect to the system.
1. `cd /vagrant`
1. `buildr clean test=no package`
1. `./server/bin/deploy`

## Deploy with test data
The Candlepin Vagrant deployer will deploy candlepin without any test data in database.
If you want to use candlepin e.g. for testing subscription-manager, then you
will need to deploy server with different options:

1. Start VM with `vagrant up`
1. Run `vagrant ssh`  to connect to the system
1. `cd vagrant`
1. `./server/bin/deploy -gta` or `./server/bin/deploy -gTa`

> Default options used by Vagrant deployer are only: `-ga`. For more
  information about deploy option run: `./server/bin/deploy -h`.

## Use of environment variables
Any environment variables set starting with 'CANDLEPIN_' will be  passed to
the ansible playbook as their name less the prefix of 'CANDLEPIN_'.
As an example, if you have set 'CANDLEPIN_TEST=2' then a variable named 'test'
will be passed to ansible with value '2'. These vars are used to modify the
behavior of our ansible playbooks.

NOTE TO DEVS: If you'd like to add any optional behavior to the ansible
playbooks, add something like 'when: my_cool_var is defined' to the optional
tasks that are dependant on the existance of the variable 'my_cool_var'.

## Profiling the vagrant machine using YourKit
1. `export CANDLEPIN_SETUP_YOURKIT=True`
1. `export CANDLEPIN_YOURKIT_LIBRARY=/path/to/libyjpagent.so`
1. Optionally specify the port to connect to the profiling agent by setting the `CANDLEPIN_YOURKIT_AGENT_PORT`.
   The default port is 35675.
1. Run `vagrant up`

## Testing QPID
In order to run the qpid tests a few additional steps must be completed manually.
1. Ensure that the rubygem-qpid_proton rpm is installed.
1. Install the proton bundle: `bundle install --with proton` This will error due to version mismatch.  
1. Update the qpid_proton gem: `bundle update qpid_proton` 
1. Install qpid (from the /vagrant/server directory): `./bin/deploy -qag`
1. Run the QPID tests: `buildr rspec:qpid` 
