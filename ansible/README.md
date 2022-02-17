# Using Vagrant for Candlepin Testing and Development
The Vagrantfile included with Candlepin is designed to provide a standardized testing and
development environment, based on various versions of CentOS.

By default, it will map in the Candlepin repo from which the Vagrant box is built, update the
system packages, install any Candlepin dependencies (including PostgreSQL and MariaDB), and
set up the user environment to be immediately usable.

Additional options can be included to setup remote debugging in Tomcat via YourKit, but
requires that YourKit has been installed on the host before provisioning.



## Prerequisites
Running the Candlepin Vagrant development image requires an up-to-date version of Vagrant, Ansible,
vagrant-sshfs, and, optionally, vagrant-hostmanager. Further, an appropriate Vagrant backend must
be installed on Linux hosts.

On Fedora installations, the following command can be run to install the necessary packages:

`sudo dnf install vagrant vagrant-libvirt vagrant-hostmanager vagrant-sshfs ansible`



## Getting Started
Once the prerequisites are installed, the Vagrant image can be brought up with the `vagrant up`
command. By default this will bring up the el8 box, but the el7 box can be brought up by specifying
it during the `up` operation:

`vagrant up el7`.

After the box is brought up, you can connect to it with the `vagrant ssh` command. If no box is
specified, it will default to the el8 box:

`vagrant ssh el8`

When the box is no longer needed, it can be destroyed with the `vagrant destroy` command. If no
box is specified, all Candlepin Vagrant boxes will be destroyed, so it's probably best to specify
exactly which box to destroy when doing so:

`vagrant destroy el8`

This is the basic lifecycle of a Vagrant box. For more details on managing boxes, see the Vagrant
CLI documentation linked below [1].



## Compiling and Deploying Candlepin
After bringing up and connecting to the desired box, the Candlepin repo from which the box was
deployed will be mapped in at `/vagrant` and symlinked to `~/devel/candlepin`. Navigate to
either directory and deploy Candlepin using the deploy command:

`./bin/deployment/deploy`

This will compile, configure, and run Tomcat and Candlepin using default Candlepin settings.
For further details on compiling and testing Candlepin, see the developer deployment documentation
linked below [2].



## Deploying Candlepin with Ansible
By default, the Ansible role used to provision the Vagrant boxes will not deploy Candlepin. However,
if this is desired, environment variables can be provided on the command line to trigger deployment
during provisioning:

- `cp_deploy`: whether or not to deploy Candlepin as part of the provisioning step; defaults to false
- `cp_deploy_args`: the arguments to pass to the Candlepin deploy tool; defaults to "-gta"

By setting "cp_deploy" to true, Candlepin will be deployed with the default arguments of "-gta" to
regenerate the database schema, regenerate the default candlepin.conf configuration, and inject
test data. This can be done by either exporting the environment variable and setting it to true, or
assigning it temporarily on the command line while bringing up or provisioning the target box:

`cp_deploy=true vagrant up el8`

Candlepin's default configuration assumes PostgreSQL will be used as its database, so
if it was not configured during the provisioning step, the deploy arguments will need to be adjusted
accordingly:

`cp_deploy=true cp_deploy_args="-gtma" vagrant up el8`



## Configuring the box for YourKit profiling
If the host machine has YourKit installed, the Candlepin Ansible role can be used to configure the
Vagrant boxes for remote debugging. To enable this, two variables must be set:

- `cp_configure_debugging`: controls whether or not Tomcat will be configured for remote debugging and
  profiling via YourKit; defaults to true
- `cp_yourkit_library`: the path to the YourKit Java profiling agent on the host system; defaults to
  "/opt/yjp/bin/linux-x86-64/libyjpagent.so"

If `cp_configure_debugging` is set to false, or the file specified by the `cp_yourkit_library`
variable isn't readable as a file, YourKit configuration will be skipped. To ensure both are set
properly for a given system, these can be explicitly set on the command line while a Vagrant box
is being brought up or provisioned:

`cp_configure_debugging=true cp_yourkit_library="/path/to/linux-x86-64/libyjpagent.so" vagrant up el8`

Note that regardless of the host system being used to bring up the Vagrant boxes, the YourKit
library provided *must* be the Linux-compatible x86-64 variant.

If Tomcat was already running in the Vagrant box before provisioning, it may require a restart for
the profiler to become active.

Once the Vagrant box has been configured, a remote profiling profile must be added in YourKit. Click
the plus icon labeled "Remote profile application..." to add a new connection, and then enter the
following details:

- Provide a meaningful name for the connection
- Enter the hostname or IP address of the Vagrant box running the Tomcat/Candlepin instance to
  profile
- Change the "application discovery method" to "advanced"
- Enter "vagrant" (all lower case) as the SSH user
- Enter "22" as the SSH port
- Click "Authentication settings..." next to the SSH user
- Change the authentication method to "private key"
- Locate the private key Vagrant generates for the SSH connections to the desired box, located at
  `{candlepin_repo}/.vagrant/machines/{box_name}/libvirt/private_key`, where `{candlepin_repo}` is
  the directory on the host system where the Candlepin repo has been checked out, and `{box_name}`
  is the name of the desired Vagrant box (i.e. el7 or el8).
- Leave the passphrase field empty

At this point, the connection should be usable for profiling.


## Custom provisioning tasks
Though the Candlepin Ansible role attempts to set up most everything needed to test and work on
Candlepin, it may not create the perfect environment for all developers. While some may be
familiar enough with Ansible create a new ad-hoc playbook or role and run it against the Vagrant
boxes themselves, it's somewhat tedious to set up. To facilitate rapid changes, the playbook used
by Vagrant to invoke the Candlepin role also supports running a custom role named "candlepin_dev"
if it is present in the `{candlepin_repo}/ansible/roles` directory.

The "candlepin_dev" role is exected to follow normal Ansible role conventions, and aside from the
optional nature of its execution, has no special properties.

To create a simple task, create `candlepin_dev` directory containing `tasks` directory, which
contains a text file named `main.yml`. The full path to "main.yml" should be as follows:
`{candlepin_repo}/ansible/roles/candlepin_dev/tasks/main.yml` where `{candlepin_repo}` is the
location of the Candlepin git repo.

Next, open the `main.yml` file and add your desired Ansible tasks:

```
# My custom Candlepin Vagrant box tasks
---
- name: "Hello World!"
  command: echo "do a barrel roll"
```

Then, reprovision (or destroy and re-create) the box to execute the tasks in the role:

`vagrant provision el8`

The tasks can get as complicated as permitted by Ansible, however that is beyond the scope of this
document. For further details on creating an Ansible role, see the details at the document linked
below [3].



## Resources
1. https://www.vagrantup.com/docs/cli
1. https://www.candlepinproject.org/docs/candlepin/developer_deployment.html
1. https://docs.ansible.com/ansible/latest/user_guide/playbooks_reuse_roles.html
