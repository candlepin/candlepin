#!/usr/bin/env ruby

require 'optparse'
require 'json'

optparse = OptionParser.new do |opts|
    opts.banner = "Usage: convert_checkin -i [FILE] -o [FILE]"

    opts.on('-i [FILE]', '--input [FILE]', "The input checkin report in sync API format") do |opt|
        @input = opt
    end

    opts.on('-o [FILE]', '--output [FILE]', "The name of the resulting output file in Async API format.") do |opt|
        @output = opt
    end

    opts.on( '-h', '--help', 'Display help and exit' ) do
        puts opts
        exit
    end
end

optparse.parse!

if @input == nil or @output == nil
    puts optparse
    exit
end

input_data = JSON(File.read(@input), {})

hypervisors = input_data['hypervisors']
results = []

hypervisors.each { |h|
  hypervisor = {"hypervisorId" => { "hypervisorId" => h['uuid'] }, "guestIds" => []}
  h['guests'].each { |g|
    guest = {"guestId" => g['guestId'], "state" => g['state']}
    hypervisor['guestIds'].push(guest)
  }
  results.push(hypervisor)
}

results = {"hypervisors" => results}
File.write(@output, results.to_json)
