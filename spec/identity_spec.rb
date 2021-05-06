require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

class OpenSSL::X509::Certificate

  # Shorthand for looking at extensions by oid
  def [](extension)
    load_extensions
    @extensions[extension]
  end

  private

  def load_extensions
    @extensions ||= Hash[extensions.collect { |ext| [ext.oid, ext.value] }]
  end
end

describe 'Identity Certificate' do

  include CandlepinMethods

  before(:each) do
    owner = create_owner random_string('test_owner')
    user = user_client(owner, random_string("user"))
    @consumer = user.register(random_string("consumer"))
    @identity_cert = OpenSSL::X509::Certificate.new(@consumer.idCert.cert)
  end

  it 'should exist after registration' do
    @identity_cert.should_not be_nil
  end

  it 'should have the same CN as the consumer\'s UUID' do
    @identity_cert.subject.to_s.should include("CN=#{@consumer.uuid}")
  end

  it 'should contain the consumer\'s name' do
    @identity_cert['subjectAltName'].should include(@consumer.name)
  end

  it 'should be pre-dated' do
    before = (Time.now - 59 * 60)
    cert_time = @identity_cert.not_before
    cert_time.should < before
  end

  it 'contains the consumer name and uuid in subject alternative names' do
    e = @identity_cert.extensions.select { |e| e.oid == 'subjectAltName' }.first
    expect(e).to_not be_nil
    name = @consumer['name']
    uuid = @consumer['uuid']

    expect(e.value).to match(/CN=#{name}/)
    expect(e.value).to match(/CN=#{uuid}/)
  end
end
