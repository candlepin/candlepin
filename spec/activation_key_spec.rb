require 'spec_helper'
require 'candlepin_scenarios'

def hash_diff(h1, h2)
  h1.keys.inject({}) do |memo, key|
    unless h1[key] == h2[key]
      memo[key] = [h1[key], h2[key]]
    end
    memo
  end
end

# XXX: all these tests need work (but so do tokens)
describe 'Activation Keys' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @product = create_product(nil, random_string('some_product'))
    @product2 = create_product(nil, random_string('some_product'))

    #this owner is used to test restrictions
    mallory = create_owner random_string('test_owner')
    @mallory_client = user_client(mallory, random_string('testuser'))

    create_pool_and_subscription(@owner['key'], @product.id, 37, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @product2.id, 37)

    @pool = @cp.list_pools(:owner => @owner.id, :product => @product['id']).first
    @pool2 = @cp.list_pools(:owner => @owner.id, :product => @product2['id']).first

    expect(@pool).to_not be_nil
    expect(@pool2).to_not be_nil

    @activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'))

    expect(@activation_key).to_not be_nil
    expect(@activation_key['id']).to_not be_nil
  end

  it 'should allow activation key field filtering' do
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'))
    key_full = @cp.get("/activation_keys/#{activation_key.id}")
    key_filtered = @cp.get("/activation_keys/#{activation_key.id}?exclude=name")
    newdict = hash_diff(key_full, key_filtered)
    # There should only be one changed key
    newdict.keys.size.should == 1
    # there should be no 'name' field in the new response
    newdict["name"][1].should == nil
  end

  it 'should set autoAttach on activation key' do
    @activation_key['autoAttach'].should == nil
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), nil, true)
    activation_key['autoAttach'].should == true
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), nil, false)
    activation_key['autoAttach'].should == false
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

  it 'should not allow updating special character as name' do
    @activation_key['name'] = "foo%%Bar$$"
    owner_client = user_client(@owner, random_string('dummyUser'))

    expect {
      @activation_key = owner_client.update_activation_key(@activation_key)
    }.to raise_exception(RestClient::BadRequest)
  end

  it 'should not allow updating empty string as name' do
    @activation_key['name'] = ""
    owner_client = user_client(@owner, random_string('dummyUser'))

    expect {
      @activation_key = owner_client.update_activation_key(@activation_key)
    }.to raise_exception(RestClient::BadRequest)
  end

  it 'should allow updating hypen and underscore string as name' do
    @activation_key['name'] = "test_activation-1"
    owner_client = user_client(@owner, random_string('dummyUser'))

    @activation_key = owner_client.update_activation_key(@activation_key)

    expect(@activation_key['name']).to eq('test_activation-1')
  end

  it 'should allow updating of descriptions' do
    @activation_key['description'] = "very descriptive text"
    @activation_key = @cp.update_activation_key(@activation_key)
    @activation_key['description'].should == "very descriptive text"

    owner_client = user_client(@owner, random_string('testuser'))

    @activation_key['description'] = "more descriptive text"
    @activation_key = owner_client.update_activation_key(@activation_key)
    @activation_key['description'].should == "more descriptive text"

    @activation_key['description'] = "nope"

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

  it 'should allow pools to be added to and removed from activation keys' do
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

  it 'should allow product ids to be added to and removed from activation keys' do
    @cp.add_prod_id_to_key(@activation_key['id'], @product['id'])
    key = @cp.get_activation_key(@activation_key['id'])
    key['products'].length.should == 1
    @cp.remove_prod_id_from_key(@activation_key['id'], @product['id'])
    key = @cp.get_activation_key(@activation_key['id'])
    key['products'].length.should == 0
  end

  it 'should allow auto attach flag to be set on activation keys' do
    @cp.update_activation_key({'id' => @activation_key['id'], "autoAttach" => "true"})
    key = @cp.get_activation_key(@activation_key['id'])
    key['autoAttach'].should be true
    @cp.update_activation_key({'id' => @activation_key['id'], "autoAttach" => "false"})
    key = @cp.get_activation_key(@activation_key['id'])
    key['autoAttach'].should be false
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

  it 'should allow nil contentOverrides' do
    name = random_string('key4')
    data = {:name => name, :contentOverrides => nil}
    activation_key = @cp.new_activation_key(@owner['key'], data)

    activation_key['name'].should eq(name)
  end

  it 'should verify override name is valid' do
    # name label is invalid to override
    override = {"name" => "label", "value" => "somval", "contentLabel" => "somelabel"}
    lambda {
      @cp.add_content_overrides_to_key(@activation_key['id'], [override])
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should verify override name is below 256 length' do
    # Should reject values too large for the database
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
    create_pool_and_subscription(@owner['key'], product1.id, 30)
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
    create_pool_and_subscription(@owner['key'], product1.id, 30, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], product2.id, 30)

    service_activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), 'VIP')
    service_activation_key['serviceLevel'].should == 'VIP'

    service_activation_key['serviceLevel'] = 'Ultra-VIP'
    service_activation_key = @cp.update_activation_key(service_activation_key)
    service_activation_key['serviceLevel'].should == 'Ultra-VIP'
  end

  it 'should allow syspurpose attributes to be set on keys' do
    service_activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), nil,  false, 'test-usage', 'test-role',['test-addon1','test-addon2'] )
    service_activation_key['usage'].should == 'test-usage'
    service_activation_key['role'].should == 'test-role'
    service_activation_key['addOns'].length.should == 2
    expect(service_activation_key['addOns']).to include('test-addon1')
    expect(service_activation_key['addOns']).to include('test-addon2')
  end

  it 'should allow syspurpose attributes to be updated on keys' do
    service_activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), nil,  false, 'test-usage', 'test-role',['test-addon1','test-addon2'] )
    service_activation_key['usage'] = 'updated-usage'
    service_activation_key['role'] = 'updated-role'
    service_activation_key['addOns'] = ['updated-addon1','updated-addon2']
    service_activation_key = @cp.update_activation_key(service_activation_key)
    service_activation_key['usage'] = 'updated-usage'
    service_activation_key['role'].should == 'updated-role'
    service_activation_key['addOns'].length.should == 2
    expect(service_activation_key['addOns']).to include('updated-addon1')
    expect(service_activation_key['addOns']).to include('updated-addon2')
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

  it 'should throw an exception with malformed data' do
    @activation_key['name'] = "ObiWan"
    @activation_key['pools'] = [
      {:poolId => @pool['id'], :quantity => nil},
      {:poolId => @pool2['id'], :quantity => 5},
    ]
    @activation_key['products'] = [{:productId => @product['id'] }]
    @activation_key['contentOverrides'] = [{:contentLabel => 'hello', :name => 'value', :value => '123' }]

    output = @cp.update_activation_key(@activation_key)

    output['name'].should eq(@activation_key['name'])
    # Note: At the time of writing, updateActivationKey does not add pools, products or content overrides to
    # activation keys, so we can never verify these get added, but we CAN verify that they are deserialized
    # properly (by way of checking for exceptions)
    # output['pools'].should eq(@activation_key['pools'])
    # output['products'].should eq(@activation_key['products'])
    # output['contentOverrides'].should eq(@activation_key['contentOverrides'])

    @activation_key['pools'] = [{:poolId => nil, :quantity => nil}]

    lambda {
      @cp.update_activation_key(@activation_key)
    }.should raise_exception(RestClient::BadRequest)

    @activation_key['pools'] = []
    @activation_key['products'] = [{:productId => nil }]

    lambda {
      @cp.update_activation_key(@activation_key)
    }.should raise_exception(RestClient::BadRequest)

    @activation_key['products'] = []
    @activation_key['contentOverrides'] = [{:contentLabel => 'hello', :name => nil, :value => '123' }]

    lambda {
      @cp.update_activation_key(@activation_key)
    }.should raise_exception(RestClient::BadRequest)


    @activation_key['contentOverrides'] = [{:contentLabel => nil, :name => 'value', :value => '123' }]

    lambda {
      @cp.update_activation_key(@activation_key)
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should list activation keys with populated entity collections' do
    # Add both products and pools to the pre-generated key
    @cp.add_prod_id_to_key(@activation_key['id'], @product['id'])
    @cp.add_prod_id_to_key(@activation_key['id'], @product2['id'])
    @cp.add_pool_to_key(@activation_key['id'], @pool['id'], 1)
    @cp.add_pool_to_key(@activation_key['id'], @pool2['id'], 1)

    # Generate some more activation keys so we have multiple keys to list
    actkey2 = @cp.create_activation_key(@owner['key'], random_string('key2'))
    @cp.add_prod_id_to_key(actkey2['id'], @product['id'])
    @cp.add_prod_id_to_key(actkey2['id'], @product2['id'])

    actkey3 = @cp.create_activation_key(@owner['key'], random_string('key3'))
    @cp.add_pool_to_key(actkey3['id'], @pool['id'], 1)
    @cp.add_pool_to_key(actkey3['id'], @pool2['id'], 1)

    actkey4 = @cp.create_activation_key(@owner['key'], random_string('key4'))

    # List keys. We should get all four keys back without error
    keys = @cp.list_activation_keys()
    expect(keys.length).to be >= 4

    processed = []
    keys.each do |key|
      if processed.include? key['id']
        fail("duplicate activation keys received: #{key['id']}")
      end

      case key['id']
        when @activation_key['id']
          expect(key['products'].length).to eq(2)
          expect(key['pools'].length).to eq(2)

        when actkey2['id']
          expect(key['products'].length).to eq(2)
          expect(key['pools'].length).to eq(0)

        when actkey3['id']
          expect(key['products'].length).to eq(0)
          expect(key['pools'].length).to eq(2)

        when actkey4['id']
          expect(key['products'].length).to eq(0)
          expect(key['pools'].length).to eq(0)

        # else: unexpected (preexisting) key, ignore it
      end

      processed.push(key['id'])
    end
  end

end
