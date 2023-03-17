VAGRANTFILE_API_VERSION = "2"

ANSIBLE_TAGS_VAR = "ansible_tags"
ANSIBLE_SKIP_TAGS_VAR = "ansible_skip_tags"
ANSIBLE_VAR_PREFIX = "cp_"

ANSIBLE_VARS = {
  :ansible_user => "vagrant",
  :candlepin_user => "vagrant",
  :candlepin_home => "/vagrant",

  :cp_configure_postgresql => true,
  :cp_configure_mariadb => true,
  :cp_configure_user_env => true,
  :cp_configure_debugging => true,

  :cp_git_checkout => false,
  :cp_deploy => false
}

def configure_ansible_provisioning(vm_config)
  vm_config.vm.provision "ansible" do |ansible|
    # ansible.verbose = "vvv"
    ansible.compatibility_mode = "2.0"
    ansible.playbook = "ansible/candlepin.yml"
    ansible.galaxy_role_file = "ansible/requirements.yml"

    # Impl note:
    # At the time of writing, specifying the role path (as the default command does) prevents
    # collections from being installed in some environments. Since we're now reliant on a number of
    # non-core collections, it's critical that we pull both roles and collections at runtime.
    # As a side effect, this will install roles and collections in the user's ~/.ansible directory,
    # rather than in the candlepin/ansible/roles directory as it has in the past.
    ansible.galaxy_command = "ansible-galaxy install -r %{role_file} --force"

    ansible.extra_vars = ANSIBLE_VARS.clone

    # Pass through ansible variables and tags from the environment variables
    ENV.each do |key, value|
      # Pass through anything starting with the "cp_" prefix
      if key.downcase.start_with?(ANSIBLE_VAR_PREFIX)
        ansible.extra_vars[key.downcase] = value
      end

      # Pass through ansible tags
      if key.downcase == ANSIBLE_TAGS_VAR
        ansible.tags = value
      end

      # Pass through ansible tags to skip
      if key.downcase == ANSIBLE_SKIP_TAGS_VAR
        ansible.skip_tags = value
      end
    end
  end
end



Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.synced_folder ".", "/vagrant", type: "sshfs"
  config.vm.host_name = "candlepin.example.com"
  config.ssh.forward_agent = true

  # Set up the hostmanager plugin to automatically configure host & guest hostnames
  if Vagrant.has_plugin?("vagrant-hostmanager")
    config.hostmanager.enabled = true
    config.hostmanager.manage_host = true
    config.hostmanager.manage_guest = false
    config.hostmanager.include_offline = true
  end

  config.vm.provider :libvirt do |provider|
    provider.cpus = 2
    provider.memory = 4096
    provider.graphics_type = "spice"
    provider.video_type = "qxl"
  end

  config.vm.define("el8", primary: true) do |vm_config|
    vm_config.vm.box = "generic/centos8s"
    vm_config.vm.host_name = "candlepin-el8.example.com"

    # Vagrant allows to create a forwarded port mapping which allows access to a specific port
    # within the guest machine from a port on the host machine.
    #
    # Uncomment these lines to forward the Candlepin standard dev ports to this guest machine.
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8080, host: 8080
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8443, host: 8443
    vm_config.vm.provision "shell", inline: "dnf update -y dnf ca-certificates"
    configure_ansible_provisioning(vm_config)
  end

  config.vm.define("el9", autostart: false) do |vm_config|
    vm_config.vm.box = "generic/centos9s"
    vm_config.vm.host_name = "candlepin-el9.example.com"
    vm_config.vm.provision "shell", inline: "dnf update -y dnf ca-certificates"
    configure_ansible_provisioning(vm_config)
  end

end
