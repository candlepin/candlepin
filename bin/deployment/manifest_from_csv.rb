#!/usr/bin/env ruby

require 'optparse'
require 'csv'
require_relative "../../client/ruby/candlepin_api"

def random_string(prefix=nil)
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

optparse = OptionParser.new do |opts|
    opts.banner = "Usage: manifest_manipulator [OPTIONS]"
    opts.separator "Options:"
    opts.on('-i [FILE]', '--input [FILE]', "The input csv with product ids, qty and date") do |opt|
        @input = opt
    end

    @output = "/tmp"
    opts.on('-o [FILE]', '--output [FILE]', "The folder to create the manifest in. (Default = #{@output}).") do |opt|
        @output = opt
    end

    opts.on('-k [OWNER_KEY]', '--key [OWNER_KEY]', "the owner to bind from") do |opt|
        @owner_key = opt
    end

    @product = 0
    opts.on('-p N', '--product-column N', Integer, "the product column index. (Default = #{@product}") do |opt|
        @product = opt
    end

    @qty = 1
    opts.on('-q N', '--quantity-column N', Integer, "the quantity coulumn index. (Default = #{@qty})") do |opt|
        @qty = opt._i
    end

    @date = 2
    opts.on('-d N', '--date-column N', Integer, "the date column index. (Default = #{@date}") do |opt|
        @date = opt
    end

    opts.on( '-h', '--help', 'Display help and exit' ) do
        puts opts
        exit
    end
end

optparse.parse!

if @input == nil or @owner_key == nil
    puts optparse
    exit
end

@cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443)
org_admin_username = random_string("orgadmin")
org_admin_password = 'password'
@cp.create_user(org_admin_username, org_admin_password, true)
org_admin_cp = Candlepin.new(org_admin_username, org_admin_password)
facts = {
    "distributor_version" => "sat-6.0",
    "satellite_version" => "6.0",
    "system.certificate_version" => "3.0"
}
consumer = org_admin_cp.register(random_string('dummyconsumer'), "candlepin",
  nil, facts, nil, @owner_key)
puts "Created consumer: id = #{consumer['id']}, uuid = #{consumer['uuid']}"
consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'],
  'localhost', '8443')


entries = CSV.read(@input)

entries.each do |entry|
  puts "Binding product: #{entry[@product]} qty:#{entry[@qty]} date:#{entry[@date]}"
  entry[@qty].to_i.times do
    params = {:uuid => consumer['uuid']}
    params[:entitle_date] = entry[@date]
    ent = @cp.consume_product(entry[@product].strip, params)
    if ent != nil
      puts "created entitlement: #{ent[0]['id']}"
    else
      puts "error creating entitlement!"
    end
  end
end

# Make a temporary directory where we can safely extract our archive:
Dir.mkdir(@output) unless File.exists?(@output)
tmp_dir = File.join(@output, random_string('candlepin-export'))
export_dir = File.join(tmp_dir, "export")
Dir.mkdir(tmp_dir)

export_filename = consumer_cp.export_consumer(tmp_dir)
puts "Export created: #{export_filename}"

