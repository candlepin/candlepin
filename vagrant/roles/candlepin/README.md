ansible-role-candlepin
=========

An Ansible role that installs and deploys candlepin for development purposes.

Requirements
------------

Candlepin has many requirements for its developers.  You will need a Fedora or CentOS box, or RHEL with epel enabled to install this on.
It is recommmended that that box has a non-root user to run candlepin on (see variables below).


Role Variables
--------------
This boolean will install all of candlepin's dependencies for a development environment.

```
candlepin_basedps: True
```

This lets you turn on or off a fresh git checkout.  It is usesful to turn this off if you want to use a local checkout.

```
candlepin_git_pull: True
```

This boolean will run a git pull on an already checked out candlepin
```
candlepin_git_pull: True
```

Candlepin will run as the candlepin user by default but and run as any user you've set up

```
candlepin_user: candlepin
candlepin_user_home:  /home/candlepin
```

This points to the main github candlepin repo, but can be changed to your developer fork. `candlepin_checkout` is the install location of candlepin.

```
candlepin_git_repo: https://github.com/candlepin/candlepin.git
candlepin_checkout: "{{candlepin_user_home}}/candlepin"
```

This is a list of extra args to give to the candlepin deploy script.  See the [official documentation](http://www.candlepinproject.org/docs/candlepin/developer_deployment.html#script-arguments).

```
candlepin_deploy_args: "-g -a"
```

Dependencies
------------

A list of other roles hosted on Galaxy should go here, plus any details in regards to parameters that may need to be set for other roles, or variables that are used from other roles.

Example Playbook
----------------

Including an example of how to use your role (for instance, with variables passed in as parameters) is always nice for users too:

    - hosts: dev
      roles:
        - candlepin

      vars:
       candlepin_user: vagrant
       candlepin_checkout: /home/vagrant

License
-------

GPLv2

Author Information
------------------

See candlepinproject.org