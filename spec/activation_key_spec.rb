require 'spec_helper'
require 'candlepin_scenarios'

# XXX: all these tests need work (but so do tokens)
describe 'Activation Keys' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(nil, random_string('some_product'))

    #this owner is used to test restrictions
    mallory = create_owner random_string('test_owner')
    @mallory_client = user_client(mallory, random_string('testuser'))

    @sub = @cp.create_subscription(@owner['key'], @some_product['id'], 37)
    @cp.refresh_pools(@owner['key'])

    pools = @cp.list_pools
    @pool = pools.select { |p| p['owner']['key'] == @owner['key'] }.first

    @activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'))
    @activation_key['id'].should_not be_nil
  end

  it 'should allow owners to list existing activation keys' do
    keys = @cp.list_activation_keys()
    keys.length.should >= 1
  end

  it 'should allow updating of names' do
    @activation_key['name'] = "ObiWan"
    @activation_key = @cp.update_activation_key(@activation_key)
    @activation_key['name'].should == "ObiWan"

    owner_client = user_client(@owner, random_string('testuser'))

    @activation_key['name'] = "another_name"
    @activation_key = owner_client.update_activation_key(@activation_key)
    @activation_key['name'].should == "another_name"

    @activation_key['name'] = "not-gonna-happen"

    lambda {
      @mallory_client.update_activation_key(@activation_key)
    }.should raise_exception(RestClient::ResourceNotFound)

  end

  it 'should allow superadmin to delete their activation keys' do
    @cp.delete_activation_key(@activation_key['id'])
  end

  it 'should allow owner to delete their activation keys' do
    owner_client = user_client(@owner, random_string('testuser'))
    owner_client.delete_activation_key(@activation_key['id'])
  end

  it 'should not allow wrong owner to delete activation keys' do
    lambda {
      @mallory_client.delete_activation_key(@activation_key['id'])
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow pools to be added and removed to activation keys' do
    @cp.add_pool_to_key(@activation_key['id'], @pool['id'], 1)
    key = @cp.get_activation_key(@activation_key['id'])
    key['pools'].length.should == 1
    @cp.remove_pool_from_key(@activation_key['id'], @pool['id'])
    key = @cp.get_activation_key(@activation_key['id'])
    key['pools'].length.should == 0

    owner_client = user_client(@owner, random_string('testuser'))
    owner_client.add_pool_to_key(@activation_key['id'], @pool['id'])
    key = owner_client.get_activation_key(@activation_key['id'])
    key['pools'].length.should == 1
    owner_client.remove_pool_from_key(@activation_key['id'], @pool['id'])
    key = owner_client.get_activation_key(@activation_key['id'])
    key['pools'].length.should == 0

    lambda {
      @mallory_client.add_pool_to_key(@activation_key['id'], @pool['id'])
    }.should raise_exception(RestClient::ResourceNotFound)

  end

  it 'should allow overrides to be added to keys' do
    override = {"name" => "somename", "value" => "somval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(@activation_key['id'], [override])
    overrides = @cp.get_content_overrides_for_key(@activation_key['id'])
    overrides.length.should == 1
    overrides[0]['name'].should == override['name']
    overrides[0]['contentLabel'].should == override['contentLabel']
    overrides[0]['value'].should == override['value']
  end

  it 'should allow overrides to be removed from keys' do
    override = {"name" => "somename", "value" => "somval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(@activation_key['id'], [override])
    overrides = @cp.get_content_overrides_for_key(@activation_key['id'])
    overrides.length.should == 1
    @cp.remove_activation_key_overrides(@activation_key['id'], [override])
    overrides = @cp.get_content_overrides_for_key(@activation_key['id'])
    overrides.length.should == 0
  end

  it 'should verify override name is valid' do
    # name baseurl is invalid to override
    override = {"name" => "baseurl", "value" => "somval", "contentLabel" => "somelabel"}
    lambda {
      @cp.add_content_overrides_to_key(@activation_key['id'], [override])
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should verify override name is below 256 length' do
    # Should reject values too large for the databse
    override = {"name" => "a" * 256, "value" => "somval", "contentLabel" => "somelabel"}
    lambda {
      @cp.add_content_overrides_to_key(@activation_key['id'], [override])
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow release to be set on keys' do
    @cp.update_activation_key({'id' => @activation_key['id'], 'releaseVer' => "Some Release"})
    @cp.get_activation_key(@activation_key['id'])['releaseVer']['releaseVer'].should == "Some Release"
  end

  it 'should allow service level to be set on keys' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'VIP'}})
    sub = @cp.create_subscription(@owner['key'], product1['id'], 30)
    @cp.refresh_pools(@owner['key'])
    service_activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), 'VIP')
    service_activation_key['serviceLevel'].should == 'VIP'
  end

  it 'should allow service level to be updated on keys' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'VIP'}})
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'Ultra-VIP'}})
    sub1 = @cp.create_subscription(@owner['key'], product1['id'], 30)
    sub2 = @cp.create_subscription(@owner['key'], product2['id'], 30)
    @cp.refresh_pools(@owner['key'])
    service_activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), 'VIP')
    service_activation_key['serviceLevel'].should == 'VIP'

    service_activation_key['serviceLevel'] = 'Ultra-VIP'
    service_activation_key = @cp.update_activation_key(service_activation_key)
    service_activation_key['serviceLevel'].should == 'Ultra-VIP'
  end

  it 'should not allow service level to be set on keys if service level is not available' do
    lambda {
      @cp.create_activation_key(@owner['key'], random_string('test_token'), 'Not There')
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should return correct exception for contraint violations' do
    lambda {
      @cp.create_activation_key(@owner['key'], nil)
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      @cp.create_activation_key(@owner['key'], "a" * 256)
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      @cp.create_activation_key(@owner['key'], "name", "a" * 256)
    }.should raise_exception(RestClient::BadRequest)
  end

end
