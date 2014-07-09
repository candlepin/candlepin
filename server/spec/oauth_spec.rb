require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'
require 'oauth'

describe 'OAuth' do
  include CandlepinMethods

  # XXX you must set these in your candlepin.conf
  oauth_consumer = "rspec"
  oauth_secret = "rspec-oauth-secret"

  before(:each) do
    @site = "https://localhost:8443"
    @oauth_params = {
     :site => @site,
     :http_method => :post,
     :request_token_path => "",
     :authorize_path => "",
     :access_token_path => "",
    }
    @owner = create_owner random_string("oauth-owner")
    @user = create_user(@owner, "oauth-user", 'password')
    @consumer = @cp.register("oauth-consumer", :system, nil, {},
                             @user.username, @owner['key'])
  end

  def make_request(oauth_consumer, oauth_secret, uri, headers = {})
    consumer = OAuth::Consumer.new(oauth_consumer, oauth_secret, @oauth_params)

    request = Net::HTTP::Get.new("#{@site}#{uri}")
    consumer.sign!(request)
    url = URI.parse("#{@site}#{uri}")

    headers.each_pair do |k, v|
      request[k] = v
    end

    req = Net::HTTP.new(url.host, url.port)
    req.use_ssl = true
    req.verify_mode = OpenSSL::SSL::VERIFY_NONE
    req.request(request)
  end

  it 'returns a 401 if oauth user is not configured' do
    res = make_request('baduser', 'badsecret', "/candlepin/subscriptions/")
    res.code.should == '401'
  end

  it 'returns a 401 if oauth secret does not match' do
    res = make_request(oauth_consumer, 'badsecret', "/candlepin/subscriptions/")
    res.code.should == '401'
  end

  it 'lets a caller act as a user' do
    res = make_request(oauth_consumer, oauth_secret,
                       "/candlepin/users/#{@user.username}",
                       {'cp-user' => @user.username})
    res.code.should == '200'
  end

  it 'trusts the provided username' do
    username = "trustthisuser"
    res = make_request(oauth_consumer, oauth_secret,
      "/candlepin/owners/#{@owner['key']}",
      {'cp-user' => username})
    res.code.should == '200'
  end

  it 'lets a caller act as a consumer' do
    res = make_request(oauth_consumer, oauth_secret,
                       "/candlepin/consumers/#{@consumer.uuid}",
                       {'cp-consumer' => @consumer.uuid})
    res.code.should == '200'
  end

  it 'returns 401 if an unknown consumer is requested' do
    res = make_request(oauth_consumer, oauth_secret,
                       "/candlepin/consumers/#{@consumer.uuid}",
                       {'cp-consumer' => "some unknown consumer"})
    res.code.should == '401'
  end

  it 'falls back to trusted system auth if no headers are set' do
    res = make_request(oauth_consumer, oauth_secret,
                       "/candlepin/consumers/#{@consumer.uuid}")
    res.code.should == '200'
  end
end
