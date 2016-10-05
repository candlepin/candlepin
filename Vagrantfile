VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
 config.vm.box = "centos/7"
 # By default, Vagrant wants to mount the code in /vagrant with NFSv3, which will fail. Let's
 # explicitly mount the code using NFSv4.
 config.vm.synced_folder ".", "/vagrant", type: "nfs", nfs_version: 4, nfs_udp: false

 # Set up the hostmanager plugin to automatically configure host & guest hostnames 
 if Vagrant.has_plugin?("vagrant-hostmanager")
   config.hostmanager.enabled = true
   config.hostmanager.manage_host = true
   config.hostmanager.manage_guest = true
   config.hostmanager.include_offline = true
 end
 
 # Create the "candlepin" box
 config.vm.define "dev" do |dev|
    dev.vm.host_name = "candlepin.example.com"
    # Tomcat remote debug
    # config.vm.network "forwarded_port", guest: 8000, host: 8000
    # Rest access for Candlepin
    # config.vm.network "forwarded_port", guest: 8080, host: 8080
    # config.vm.network "forwarded_port", guest: 8443, host: 8443
    config.ssh.forward_x11 = true
    dev.vm.provider :libvirt do |domain|
        domain.cpus = 1
        domain.graphics_type = "spice"
        domain.memory = 2048
        domain.video_type = "qxl"

        # Uncomment the following line if you would like to enable libvirt's unsafe cache
        # mode. It is called unsafe for a reason, as it causes the virtual host to ignore all
        # fsync() calls from the guest. Only do this if you are comfortable with the possibility of
        # your development guest becoming corrupted (in which case you should only need to do a
        # vagrant destroy and vagrant up to get a new one).
        #
        # domain.volume_cache = "unsafe"
    end

    config.vm.provision "ansible" do |ansible|
      ansible.playbook = "vagrant/candlepin.yml"
    end
 end
end
