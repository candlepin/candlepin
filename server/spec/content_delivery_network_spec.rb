require 'spec_helper'
require 'candlepin_scenarios'

describe 'Content Delivery Network' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
  end

  it 'should allow content delivery network creation' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com")
    cdn.id.should_not be nil
    @cp.get_cdns.size.should == count+1
  end

  it 'should allow content delivery network update' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com")
    cdn_id = cdn.id
    cdn = update_cdn(cdn_label,
                     "Test CDN 2",
                     "https://special.cdn.test.com")
    @cp.get_cdns.size.should == count+1
    cdn.id.should == cdn_id
    cdn.url.should == "https://special.cdn.test.com"
    cdn.name.should == "Test CDN 2"
  end

  it 'should allow certificate to be put on CDN on create' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")
    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert'
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com",
                     certificate)
    cdn.id.should_not be nil
    @cp.get_cdns.size.should == count+1
    cdn.certificate['key'].should == "test-key"
    cdn.certificate['cert'].should == "test-cert"
  end

  it 'should allow certificate to be put on CDN on update' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")
    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert'
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com")
    cdn_id = cdn.id
    cdn.id.should_not be nil
    cdn = update_cdn(cdn_label, nil, nil, certificate)
    @cp.get_cdns.size.should == count+1
    cdn.certificate['key'].should == "test-key"
    cdn.certificate['cert'].should == "test-cert"
  end

  it 'should allow candlepin to create certificate serial' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")

    serial = { 'expiration' => Date.today.next_year }

    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert',
        'serial' => serial
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com",
                     certificate)
    cdn.id.should_not be nil
    @cp.get_cdns.size.should == count+1
    cdn.certificate['key'].should == "test-key"
    cdn.certificate['cert'].should == "test-cert"
    cdn.certificate['serial'].should_not be_nil
  end

  it 'should allow candlepin to update certificate serial' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")

    serial = { 'expiration' => Date.today.next_year }

    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert',
        'serial' => serial
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com",
                     certificate)
    cdn.id.should_not be nil

    @cp.get_cdns.size.should == count+1
    cdn.certificate['key'].should == "test-key"
    cdn.certificate['cert'].should == "test-cert"
    cdn.certificate['serial'].should_not be_nil

    updated_key = 'm_test_key'
    updated_cert = 'm_test_cert'
    updated_expiration = Date.today.next_month.next_year
    certificate['key'] = updated_key
    certificate['cert'] = updated_cert
    certificate['serial']['expiration'] = updated_expiration

    cdn = update_cdn(cdn_label, nil, nil, certificate)
    @cp.get_cdns.size.should == count+1
    cdn.certificate['key'].should == updated_key
    cdn.certificate['cert'].should == updated_cert
    Date.parse(cdn.certificate['serial']['expiration']).should == updated_expiration
  end

  it 'should allow deletion with certificate' do
    count = @cp.get_cdns.size
    cdn_label = random_string("test-cdn")

    serial = { 'expiration' => Date.today.next_year }

    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert',
        'serial' => serial
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com",
                     certificate)
    cdn.id.should_not be nil
    @cp.get_cdns.size.should == count+1

    @cp.delete_cdn(cdn_label)
    @cp.get_cdns.size.should == count
  end

end
