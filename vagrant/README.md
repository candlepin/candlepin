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

## Prerequisits
Running the Candlepin Vagrant deployer needs an up to date version of Vagrant 
and Ansible. If the vagrant-hostmanager pluign is installed then the hostname
for the development candlepin box will automatically be added to your hosts file. 

The NFS service requires +x attribute on all parent directories in order to mount 
the source directory. If you are running into problems with the NFS mount this would 
be the first thing to check.  

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
