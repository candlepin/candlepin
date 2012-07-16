#!/usr/bin/ruby

require 'rubygems'
require 'fileutils'
require 'json'
require 'pp'
require 'erb'

api_file = File.open(ARGV[0], 'r')
apistruct = JSON.load(api_file)

def empty?(value)
  value.nil? or value == ""
end

def error(method, description)
  puts("E: #{method['method']} - #{description}")
end

apistruct.each do |method|
  error(method, "missing summary") if empty? method['summary']
  error(method, "missing description") if empty? method['description']
  error(method, "missing returns") if empty? method['returns']

  method['httpStatusCodes'].each do |code|
    error(method, "missing description for code #{code['statusCode']}") if empty? code['description']
  end
end

