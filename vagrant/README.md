# Testing Candlepin with Vagrant
The vagrant test setup performs the following actions:
* Deploy a Centos 7 vm
* sshfs mount your local source directory into the VM
* Configure Tomcat remote debugging
* Install Postgres and configure the candlepin user
  without an external messaging broker).
* Compile & install Candlepin
* Forward Ports to your local system for debugging
  * Tomcat remote debug on port 8000
  * REST access for Candlepin on port 8080 and 8443
* Optionally, setup yourkit profiler to enable profiling of the vm

## Prerequisites
Running the Candlepin Vagrant deployer needs an up to date version of Vagrant, vagrant-sshfs, 
and Ansible. If the vagrant-hostmanager pluign is installed then the hostname
for the development candlepin box will automatically be added to your hosts file.

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
1. If you are on candlepin master branch use`./bin/deployment/deploy` or else use `./server/bin/deploy` (these commands will automatically use gradle to recompile before deploying)

## Deploy with test data
The Candlepin Vagrant deployer will deploy candlepin without any test data in database.
If you want to use candlepin e.g. for testing subscription-manager, then you
will need to deploy server with different options:

1. Start VM with `vagrant up`
1. Run `vagrant ssh`  to connect to the system
1. `cd vagrant`
1. If you are on candlepin master branch use `./bin/deployment/deploy -gta` or else use `./server/bin/deploy -gta`

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

## Switching Java version in vagrant box

Candlepin uses Java 11 from version 3.2.0 & onwards. We have an option to switch Java version back & forth for testing older/newer candlepin branches.  

NOTE - By default vagrant box boots up with Java 11.

To switch Java version back & forth we need to manually configure few things.

1. Set Java version (Select Java 8 or Java 11).
   * `sudo update-alternatives --config java`
   * `sudo update-alternatives --config javac`

2. Set `JAVA_HOME` in `bashrc`.

3. Manually edit `tomcat.conf` to update these config properties.
    * Set Java Home
       * `JAVA_HOME="<PATH>"`
    * Set JVM Options
       * For Java 8
        `JAVA_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n`
       * For Java 11
        `JAVA_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,address=*:8000,server=y,suspend=n`

4. Restart Tomcat
