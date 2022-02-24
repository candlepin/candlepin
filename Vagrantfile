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
    ansible.playbook = "ansible/candlepin.yml"
    ansible.galaxy_role_file = "ansible/requirements.yml"
    # ansible.verbose = "v"
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

  config.vm.define("el7", autostart: false) do |vm_config|
    vm_config.vm.box = "centos/7"
    vm_config.vm.host_name = "candlepin-el7.example.com"

    # Uncomment these lines for forward the Candlepin standard dev ports to this guest
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8080, host: 8080
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8443, host: 8443

    vm_config.vm.provision "shell", inline: "yum update -y yum python ca-certificates"
    configure_ansible_provisioning(vm_config)
  end

  config.vm.define("el8", primary: true) do |vm_config|
    vm_config.vm.box = "centos/stream8"
    # vm_config.vm.box_url = "https://cloud.centos.org/centos/8-stream/x86_64/images/CentOS-Stream-Vagrant-8-20220125.1.x86_64.vagrant-libvirt.box"
    vm_config.vm.host_name = "candlepin-el8.example.com"

    # Uncomment these lines for forward the Candlepin standard dev ports to this guest.
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8080, host: 8080
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8443, host: 8443

    vm_config.vm.provision "shell", inline: "dnf update -y dnf ca-certificates"
    configure_ansible_provisioning(vm_config)
  end

  config.vm.define("f35", autostart: false) do |vm_config|
    vm_config.vm.box = "fedora/35-cloud-base"
    vm_config.vm.host_name = "candlepin-f35.example.com"

    # Uncomment these lines for forward the Candlepin standard dev ports to this guest
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8080, host: 8080
    # vm_config.vm.networking "forwarded_port", protocol: "tcp", guest: 8443, host: 8443

    vm_config.vm.provision "shell", inline: "dnf update -y dnf python ca-certificates"
    configure_ansible_provisioning(vm_config)
  end

end
