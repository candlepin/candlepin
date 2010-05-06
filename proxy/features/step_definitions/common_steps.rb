require 'spec/expectations'
require 'candlepin_api'

CONF_FILE = "cucumber.conf"

# Global variable for config hash. Used so we only have to read the test conf
# file once.
$config = nil

Before do
    if $config.nil?
        initialize_config()
    end
    @candlepin = Candlepin.new
    @candlepin.use_credentials($config['username'], $config['password'])
    @test_owner = @candlepin.create_owner(
        gen_random_string('testowner'))['owner']
end

After do
    @candlepin.use_credentials($config['username'], $config['password'])
    @candlepin.delete_owner(@test_owner['id'])
end

# Reads the cucumber.conf file for test config if it exists, uses default
# config if not.
def initialize_config
    config_file = File.expand_path("../", File.dirname(__FILE__))
    config_file = File.join(config_file, "cucumber.conf")
    $config = {}
    if File.exists?(config_file) then
        print("Using test config from: %s\n" % config_file)
        File.open(config_file, 'r') do |properties_file|
            properties_file.read.each_line do |line|
                line.strip!
                # Skip comments:
                if (line[0] != ?# and line[0] != ?=)
                    i = line.index('=')
                    if (i)
                        $config[line[0..i - 1].strip] = line[i + 1..-1].strip
                    else
                        $config[line] = ''
                    end
                end
            end      
        end
    else
        print("No config file found, using default test config\n")
        $config = {}
        $config['username'] = 'root'
        $config['password'] = 'root'
        $config['hostname'] = 'localhost'
        $config['port'] = '443'
    end

    # Quick check to make sure config contains the properties we use:
    for key in ['username', 'password', 'hostname', 'port']:
        if not $config.has_key?(key)
            raise "Missing test config property: %s" % key
        end
    end
end

def gen_random_string(prefix)
    "%s-%s" % [prefix, rand(100000)]
end

Then /My ([\w ]+) exists/ do |property|
    @candlepin.send(to_name(property)).should_not be_nil
end

Then /My ([\w ]+)'s (\w+) is "([^"]+)"/ do |entity, property, expected|
    self.send(to_name(entity))[property].should == expected
end

def to_name(text)
    text.downcase.gsub(/\s/, '_')
end

