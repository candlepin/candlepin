require 'spec/expectations'
require 'candlepin_api'
require 'zip/zip'

Given /I am a consumer "([^\"]*)" of type \"candlepin\""/ do |consumer_name|
  Given "I am logged in as \"#{user_name}\""
  set_consumer(@current_owner_cp.register(consumer_name, :candlepin))
end

When /I perform export/ do 
  @export_filename = @consumer_cp.export_consumer
  puts @export_filename
end

Then /I get an archived extract of data/ do
  File.exist?(@export_filename).should == true
  @export_dir = unzip_export_file(@export_filename)
  
  assert_consumer_types
  assert_consumer
  assert_entitlements
  assert_entitlement_certificates
  assert_rules
end

When /I perform import/ do
end

Then /I have data from extract in candlepin/ do
  @export_dir = unzip_export_file("extract.zip")
  
  assert_consumer_types
  assert_consumer
  assert_entitlements
  assert_entitlement_certificates
  assert_pools
  assert_rules
end

After do
  File.delete(@export_filename) if @export_filename != nil && File.exist?(@export_filename)
  FileUtils.rm_rf(@export_dir) if @export_dir != nil && File.exist?(@export_dir)
end


def assert_entitlements
  entitlements_dir = File.join(@export_dir, 'entitlements')
  
  available_entitlements ||= {}
  @consumer_cp.list_entitlements.each do |e| 
    available_entitlements[e['productId']] = e
  end
  exported_entitlements = files_in_dir(entitlements_dir)
    
  exported_entitlements.size.should == available_entitlements.size
  
  exported_entitlements.each do |file|
    exported_entitlement = parse_file(File.join(entitlements_dir, file))
    available_entitlements[exported_entitlement['productId']].should_not == nil
  end
end

def assert_entitlement_certificates
  entitlement_certs_dir = File.join(@export_dir, 'entitlement_certificates')
  
  exported_entitlement_certs = files_in_dir(entitlement_certs_dir)
  
  available_certs ||= {}
  @consumer_cp.get_certificates.each do |c|
    available_certs[c['serial']] = c
  end
  
  exported_entitlement_certs.size.should == available_certs.size
  
  exported_entitlement_certs.each do |file|
    exported_cert = load_file(File.join(entitlement_certs_dir, file))
    exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
  end
end

def assert_consumer_types
  consumer_types_dir = File.join(@export_dir, 'consumer_types')
  
  exported_consumer_types = files_in_dir(consumer_types_dir)
  
  available_consumer_types ||= {}
  @candlepin.get_consumer_types.each do |t|
    available_consumer_types[t['label']] = t
  end
  
  exported_consumer_types.size.should == available_consumer_types.size
  
  exported_consumer_types.each do |file|
    consumer_type = parse_file(File.join(consumer_types_dir, file))
    available_consumer_types[consumer_type['label']].should_not == nil
  end
end

def assert_consumer
  exported_consumer = parse_file(File.join(@export_dir, 'consumer.json'))
  exported_consumer['uuid'].should == @consumer['uuid']
end

def assert_rules
  Base64.decode64(@candlepin.list_rules).should == load_file(File.join(@export_dir, "rules/rules.js"))
end

def assert_pools
  entitlements_dir = File.join(@export_dir, 'entitlements')
  
  available_pools ||= {}
  @consumer_cp.get_pools(@consumer['uuid']).each do |p|
    available_pools[p['productId']] = p
  end
  exported_entitlements = files_in_dir(entitlements_dir)
    
  exported_entitlements.size.should == available_pools.size
  
  exported_entitlements.each do |file|
    exported_entitlement = parse_file(File.join(entitlements_dir, file))
    available_pools[exported_entitlement['productId']].should_not == nil
  end
end

def parse_file(filename)
  JSON.parse(load_file(filename))
end

def load_file(filename)
  contents = ''
  f = File.open(filename, "r")
  f.each_line do |line|
    contents += line
  end
  return contents
end

def unzip_export_file(filename)
  Zip::ZipFile::open(filename) do |zf| 
     zf.each do |e|
       fpath = File.join(Dir.pwd, e.name) 
       FileUtils.mkdir_p(File.dirname(fpath)) 
       zf.extract(e, fpath) 
     end 
  end
  filename.split('.zip')[0]
end

def files_in_dir(dir_name)
  Dir.entries(dir_name).select {|e| e != '.' and e != '..' }
end   
  
