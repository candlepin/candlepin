#! /usr/bin/env ruby
require 'net/http'
require 'webrick'
require 'webrick/https'

# Use the latest minitest
gem 'minitest'
require 'minitest/autorun'
require 'shoulda/context'

require '../candlepin'

module Candlepin
  # Note that these are functional tests so they require
  # a running Candlepin.

  module Functional
    class TestClient < Minitest::Test
      context 'a client' do
        should 'get a status as JSON' do
          simple_client = SimpleClient.new.client
          res = simple_client.get('/status')
          assert(res.content.key?('version'))
        end

        should 'get owners with basic auth' do
          user_client = UserClient.new.client
          res = user_client.get('/owners')
          refute_empty(res.content)
          assert(res.content.first.key?('id'))
        end

        should 'fail with bad password' do
          user_client = UserClient.new(:password => nil).client
          res = user_client.get('/owners')
          assert_equal(401, res.status_code)
        end
      end
    end
  end

  module Unit
    class TestClientUnit < Minitest::Test
      TEST_PORT = 11999
      attr_accessor :server

      context 'a client' do
        setup do
          key = OpenSSL::PKey::RSA.new(File.read('test.key'))
          cert = OpenSSL::X509::Certificate.new(File.read('test.cert'))

          @server = WEBrick::HTTPServer.new(
            :BindAddress => 'localhost',
            :Port => TEST_PORT,
            :SSLEnable => true,
            # Uncomment the below and comment SSLCertificate and SSLPrivateKey to generate
            # a new cert every test.  I elected to go with a static cert for speed and to
            # so I could provide the cert as a CA to the client
            # :SSLCertName => [ %w[CN localhost] ],
            :SSLPrivateKey => key,
            :SSLCertificate => cert,
            :Logger => WEBrick::BasicLog.new(nil, WEBrick::BasicLog::FATAL),
            :AccessLog => [],
          )
          @server_thread = Thread.new do
            server.start
          end
        end

        should 'use CA if given' do
          server.mount_proc('/candlepin/status') do |req, res|
            res.body = '{ "message": "Hello" }'
            res['Content-Type'] = 'text/json'
          end
          simple_client = SimpleClient.new(:ca_path => 'test.cert', :port => TEST_PORT, :insecure => false).client
          res = simple_client.get('/status')
          assert_equal("Hello", res.content['message'])
        end

        should 'fail to connect if no CA given in strict mode' do
          server.mount_proc('/candlepin/status') do |req, res|
            res.body = '{ "message": "Hello" }'
            res['Content-Type'] = 'text/json'
          end
          simple_client = SimpleClient.new(:port => TEST_PORT, :insecure => false).client
          assert_raises(OpenSSL::SSL::SSLError) do
            simple_client.get('/status')
          end
        end

        teardown do
          server.shutdown
          @server_thread.kill unless @server_thread.nil?
        end
      end
    end
  end
end
