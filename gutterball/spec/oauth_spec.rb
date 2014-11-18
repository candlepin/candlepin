require 'rubygems'
require 'rest_client'
require 'oauth'



describe 'OAuth' do
  # include CandlepinMethods

  # XXX you must set these in your gutterball.conf
  oauth_consumer = "rspec"
  oauth_secret = "rspec-oauth-secret"

  before(:each) do
    @site = "https://localhost:8443"
    @oauth_params = {
     :site => @site,
     :http_method => :get,
     :request_token_path => "",
     :authorize_path => "",
     :access_token_path => "",
    }
  end


  def make_anon_request(uri, headers = {})
    url = URI.parse("#{@site}#{uri}")

    request = Net::HTTP::Get.new("#{@site}#{uri}")

    headers.each_pair do |k, v|
      request[k] = v
    end

    req = Net::HTTP.new(url.host, url.port)
    req.use_ssl = true
    req.verify_mode = OpenSSL::SSL::VERIFY_NONE
    req.request(request)
  end

  def make_oauth_request(oauth_consumer, oauth_secret, uri, headers = {})
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


  it 'returns a 200 if an anon user accesses an unprotected function' do
    res = make_anon_request('/gutterball/status')
    res.code.should == '200'
  end

  it 'returns a 401 if an anon user accesses a protected function' do
    res = make_anon_request('/gutterball/reports')
    res.code.should == '401'
  end

  it 'returns a 401 if oauth user is not configured' do
    res = make_oauth_request('baduser', 'badsecret', "/gutterball/reports/")
    res.code.should == '401'
  end

  it 'returns a 401 if oauth secret does not match' do
    res = make_oauth_request(oauth_consumer, 'badsecret', "/gutterball/reports/")
    res.code.should == '401'
  end

  it 'returns a 200 if oauth user is properly configured and provides proper credentials' do
    res = make_oauth_request(oauth_consumer, oauth_secret, "/gutterball/reports/")
    res.code.should == '200'
  end
end
