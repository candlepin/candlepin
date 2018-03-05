#!/usr/bin/env ruby

require 'optparse'

require 'set'
require 'thread'

@options = {}


# Example messages
#2017-11-20 03:37:20,737 [thread=http-bio-8443-exec-4] [req=8138a38e-1e06-4292-aaa3-e205bbba9dc5, org=, csid=bd832795] INFO  org.candlepin.common.filter.LoggingFilter - Request: verb=GET, uri=/candlepin/consumers/d3a179bb-f5b6-40e7-85bd-4f8a41e3c709
#2018-01-23 11:59:45,966 [thread=http-nio-8443-exec-4] [req=1a8d9011-d622-42e2-88cd-258e8dd1fe8c, org=, csid=] DEBUG org.candlepin.resteasy.filter.AbstractAuthorizationFilter - Request: verb=GET, uri=/candlepin/status
#2018-01-23 11:59:45,980 [thread=http-nio-8443-exec-4] [req=1a8d9011-d622-42e2-88cd-258e8dd1fe8c, org=, csid=] DEBUG org.candlepin.resteasy.filter.AbstractAuthorizationFilter - Request: GET /candlepin/status

#2017-11-20 03:37:20,762 [thread=http-bio-8443-exec-4] [req=8138a38e-1e06-4292-aaa3-e205bbba9dc5, org=Default_Organization, csid=bd832795] INFO  org.candlepin.common.filter.LoggingFilter - Response: status=200, content-type="application/json", time=25
#2018-01-23 10:53:02,594 [thread=http-nio-8443-exec-4] [req=1b825983-0bbd-47d8-b9c0-b00d16ab1d2c, org=, csid=] DEBUG org.candlepin.common.filter.LoggingFilter - Response: 200 OK (221 ms)

#Request: verb=(\w+), uri=(.+)
#Response: status=(\d+), content-type=(.+?), time=(\d+)


def process_log(filename)

  status_regex = /([0-9\- :,]+) \[thread=([^\]]+)\] \[req=([0-9a-z\-]+), org=([^,]*), csid=([^\]]*)\] (INFO|DEBUG) .+ - (?:(Request|Response): (.+))/

  req_regex = /\s*verb=([A-Z]+), uri=(.+)\s*/
  res_regex = /\s*status=(\d+), content-type=.+?, time=(\d+)\s*/
  dbg_res_regex = /\s*(\d+) .+? \((\d+) ms\)\s*/

  req_ids = Set.new
  requests = {}
  proc_requests = {}
  slow_requests = {}
  failed_requests = {}

  lines = 0

  log(LOG_INFO, "Processing file #{filename} using slow request threshold of #{@options[:slow_req_threshold]} ms")


  File.open(filename) do |fp|
    fp.each_line do |line|
      lines = lines + 1

      # Output some dots to show the process is still running when processing huge logs
      if lines % 100000 == 0
        putc '.'
      end

      sdata = status_regex.match(line)

      if sdata != nil
        req_ids << sdata[3].downcase

        if sdata[7] == 'Request'
          request = {
            :timestamp => sdata[1],
            :thread => sdata[2],
            :id => sdata[3],
            :org => sdata[4],
            :csid => sdata[5]
          }

          rdata = req_regex.match(sdata[8])

          if rdata != nil
            request[:verb] = rdata[1]
            request[:uri] = rdata[2]

            if (@options[:uri_filter] == nil || @options[:uri_filter].match(request[:uri])) &&
              (@options[:method_filter] == nil || @options[:method_filter].match(request[:verb]))

              requests[request[:id]] = request
            end
          else
            # Ignore doubled request lines in debug mode.
            if sdata[6] != 'DEBUG' || !/\s*[A-Z]+ .+/.match(sdata[8])
              log(LOG_ERROR, "Unable to parse request data for request #{request[:id]}: #{sdata[8]}")
            end
          end
        elsif sdata[7] == 'Response' # Response
          request = requests.delete(sdata[3])

          if request != nil
            proc_requests[request[:id]] = request
            rdata = (sdata[6] == 'DEBUG' ? dbg_res_regex : res_regex).match(sdata[8])

            if rdata != nil
              request[:status] = rdata[1]
              request[:time] = rdata[2]

              if request[:time].to_i >= @options[:slow_req_threshold]
                slow_requests[request[:id]] = request
              end

              if request[:status].to_i < 200 || request[:status].to_i > 299
                failed_requests[request[:id]] = request
              end
            else
              log(LOG_ERROR, "Unable to parse response data for request #{request[:id]}: #{sdata[8]}")
            end
          else
            if @options[:uri_filter] == nil && @options[:method_filter] == nil
              log(LOG_WARN, "Response found without matching request for ID: #{sdata[3]}")
            end
          end
        else
          log(LOG_ERROR, "Unexpected line type: #{sdata[7]}")
        end

      end
    end
  end

  puts unless @options[:silent]
  log(LOG_INFO, "Finished processing file. #{lines} lines processed, #{req_ids.size} requests processed")

  if !@options[:req_ids_only]
    if @options[:show_all] && !proc_requests.empty?
      log(LOG_INFO, "Request(s) processed:");

      proc_requests.each do |req_id, request|
        puts "  #{request[:timestamp]}: #{req_id} => #{request[:verb]} #{request[:uri]} -- thread: #{request[:thread]}"
      end
    end

    puts
    if !requests.empty?
      log(LOG_WARN, "Found #{requests.size} incomplete request(s):");

      requests.each do |req_id, request|
        puts "  #{request[:timestamp]}: #{req_id} => #{request[:verb]} #{request[:uri]} -- thread: #{request[:thread]}"
      end
    else
      log(LOG_INFO, "Found 0 incomplete requests");
    end

    puts
    if !slow_requests.empty?
      log(LOG_WARN, "Found #{slow_requests.size} slow request(s):");

      slow_requests.each do |req_id, request|
        puts "  #{request[:timestamp]}: #{req_id} => #{request[:verb]} #{request[:uri]} -- time: #{request[:time]}ms"
      end
    else
      log(LOG_INFO, "Found 0 slow requests");
    end

    puts
    if !failed_requests.empty?
      log(LOG_WARN, "Found #{failed_requests.size} failed request(s):");

      failed_requests.each do |req_id, request|
        puts "  #{request[:timestamp]}: #{req_id} => #{request[:verb]} #{request[:uri]} -- response: #{request[:status]}"
      end
    else
      log(LOG_INFO, "Found 0 failed requests");
    end
  else
    if @options[:show_all]
      if !proc_requests.empty?
        proc_requests.each do |req_id, request|
          puts req_id
        end
      end

      if !requests.empty?
        requests.each do |req_id, request|
          puts req_id
        end
      end
    else
      if !requests.empty?
        requests.each do |req_id, request|
          puts req_id
        end
      end

      if !slow_requests.empty?
        slow_requests.each do |req_id, request|
          puts req_id
        end
      end

      if !failed_requests.empty?
        failed_requests.each do |req_id, request|
          puts req_id
        end
      end
    end
  end

end

##############################################################################################################
# Logging
##############################################################################################################

LOG_ERROR = 'ERROR'
LOG_INFO = 'INFO'
LOG_WARN = 'WARN'
LOG_DEBUG = 'DEBUG'

def log(level, message)
  puts "#{level}: #{message}" unless @options[:silent]
end

##############################################################################################################
# Option & Argument parsing
##############################################################################################################

# Set up & parse options
optparse = OptionParser.new do |opts|
  file = File.basename(__FILE__)
  opts.banner = "Usage: #{file} [options] [file]"

  @options[:slow_req_threshold] = 5000
  opts.on('--srt [TIME]', 'Threshold to use for flagging a request as "slow," in milliseconds; defaults to 5000') do |opt|
    @options[:slow_req_threshold] = opt.to_i
  end

  @options[:uri_filter] = nil
  opts.on('--uri [REGEX]', 'Regular expression to filter requests by URI; defaults to nil') do |opt|
    @options[:uri_filter] = Regexp.new(opt)
  end

  @options[:method_filter] = nil
  opts.on('--method [REGEX]', 'Regular expression to filter requests by request method; defaults to nil') do |opt|
    @options[:method_filter] = Regexp.new(opt)
  end

  @options[:req_ids_only] = false
  opts.on('--idsonly', 'Only display request IDs; defaults to false') do |opt|
    @options[:silent] = true
    @options[:req_ids_only] = true
  end

  @options[:show_all] = false
  opts.on('--showall', 'Displays all processed request information; defaults to false') do |opt|
    @options[:show_all] = true
  end

  opts.on('-?', '--help', 'Displays command and option information') do
    puts opts
    puts
    exit
  end
end

optparse.parse!

# Get file argument
candlepin_log = ARGV.first || '/var/log/candlepin/candlepin.log'

# Verify file exists
if !File.file?(candlepin_log) || !File.readable?(candlepin_log)
  log(LOG_ERROR, "File does not exist or cannot be read: #{candlepin_log}")
  exit 1
end

process_log(candlepin_log)

