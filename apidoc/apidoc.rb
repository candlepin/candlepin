#!/usr/bin/ruby

require 'fileutils'
require 'json'
require 'pp'
require 'erb'

api_file = File.open(ARGV[0], 'r')
apistruct = JSON.load(api_file)

# group methods by resource
resources = {}
apistruct.each do |method|
  url = method['url']
  resource = '/' + (url.split('/')[1] || '')
  
  resources[resource] ||= []
  resources[resource] << method
end

def get_overview_binding(resources)
  return binding
end

def get_resource_binding(name, resource)
  return binding
end

def get_method_binding(method)
  return binding
end


output_dir = "target/apidoc/"
Dir::mkdir(output_dir) unless File.exists?(output_dir)

template_dir = "apidoc/"

puts "Writing overview page"
overview_template_file = File.open(template_dir + "overview.erb", 'r')
overview_template = ERB.new(overview_template_file.read)

output_file = File.open(output_dir + "index.html", 'w')
output_file << overview_template.result(get_overview_binding(resources))

puts "Writing resources:"
resource_template_file = File.open(template_dir + "resource.erb", 'r')
resource_template = ERB.new(resource_template_file.read)

method_template_file = File.open(template_dir + "method.erb", 'r')
method_template = ERB.new(method_template_file.read)

resources.each_key do |name|
  puts "  " + name
  if name == "/"
    path_name = "root"
  else
    path_name = name
  end
  
  resource_dir = output_dir + path_name + "/"
  Dir::mkdir(resource_dir) unless File.exists?(resource_dir)

  resources[name].each do |method|
    #make a nice url friendly name for it
    method['page'] = method['httpVerbs'][0].downcase +
        method['url'].gsub('{','').gsub('}','')

    method_dir = output_dir + method['page']
    FileUtils.mkdir_p(method_dir) unless File.exists?(method_dir)

    method_file = File.open(method_dir + "/index.html", 'w')
    method_file << method_template.result(get_method_binding(method))
  end


  resource_file = File.open(resource_dir + "index.html", 'w')
  resource_file << resource_template.result(
      get_resource_binding(name, resources[name]))
end
