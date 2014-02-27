require 'spec_helper'
require 'candlepin_scenarios'

describe 'Hypervisor Resource', :type => :virt do
  include CandlepinMethods
  include VirtHelper

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?
    @expected_host = random_string("Host1")
    @expected_guest_ids = [@uuid1, @uuid2]

    # Check in with initial hypervisor to create host consumer and associate guests.
    host_guest_mapping = get_host_guest_mapping(@expected_host, @expected_guest_ids)
    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    results.created.size.should == 1

    @host_uuid = results.created[0]['uuid']
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host, @expected_guest_ids)

    @cp.get_consumer_guests(@host_uuid).length.should == 2
    @host_client = registered_consumer_client(consumer)
    @host_client.consume_pool(@virt_limit_pool['id'], {:quantity => 1})

    @consumer = consumer_client(@user, random_string("consumer"))

    # For testing some cross-org hypervisor check-ins:
    @owner2 = create_owner random_string('virt_owner2')
    @user2 = user_client(@owner2, random_string('virt_user2'))
  end

  it 'should add consumer to created when new host id and no guests reported' do
    consumer_uuid = random_string('host')
    mapping = get_host_guest_mapping(consumer_uuid, [])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.created[0].name.should == consumer_uuid

    # Test get_owner_hypervisors works, should return all
    hypervisors = @user.get_owner_hypervisors(@owner['key'])
    hypervisors.size.should == 2
    # Test lookup with hypervisor ids
    hypervisors = @user.get_owner_hypervisors(@owner['key'], [consumer_uuid])
    hypervisors.size.should == 1
    # Test lookup with nonexistant hypervisor id
    hypervisors = @user.get_owner_hypervisors(@owner['key'], ["probably not a hypervisor"])
    hypervisors.size.should == 0
  end

  it 'should add consumer to created when new host id and guests were reported' do
    consumer_uuid = random_string('host')
    mapping = get_host_guest_mapping(consumer_uuid, ['g1'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.created[0].name.should == consumer_uuid
  end

  it 'should not add new consumer when create_missing is false' do
    consumer_uuid = random_string('host')
    mapping = get_host_guest_mapping(consumer_uuid, ['g1'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping, false)
    # Should only  have a result entry for failed.
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 1
  end

  it 'should add consumer to updated when guest ids are updated' do
    mapping = get_host_guest_mapping(@expected_host, ['g1', 'g2'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    # Should only  have a result entry for updated.
    result.created.size.should ==0
    result.updated.size.should == 1
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.updated[0].name.should == @expected_host
  end

  it 'should add consumer to unchanged when same guest ids are sent' do
    mapping = get_host_guest_mapping(@expected_host, @expected_guest_ids)
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    # Should only  have a result entry for unchanged.
    result.created.size.should ==0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.unchanged[0].name.should == @expected_host
  end

  it 'should add consumer to unchanged when comparing empty guest id lists' do
    consumer_uuid = random_string('host')
    mapping = get_host_guest_mapping(consumer_uuid, [])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    result.created.size.should == 1
    result.created[0].name.should == consumer_uuid

    # Do the same update with [] and it should be considered unchanged.
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0
    # verify our unchanged consumer is correct.
    result.unchanged[0].name.should == consumer_uuid
  end

  it 'should add host and associate guests' do
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host, @expected_guest_ids)
  end

  it 'should update host guest ids as consumer' do
    update_guest_ids_test(@consumer)
  end

  it 'should update host guest ids as user' do
    update_guest_ids_test(@user)
  end

  def update_guest_ids_test(client)
    # Update the guest ids
    new_guest_id = 'Guest3'
    updated_guest_ids = [@uuid2, new_guest_id]
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, updated_guest_ids)
    results = client.hypervisor_check_in(@owner['key'], updated_host_guest_mapping)
    # Host consumer already existed, no creation occurred.
    results.created.size.should == 0
    # Ensure that we are returning the updated consumer correctly.
    check_hypervisor_consumer(results.updated[0], @expected_host, updated_guest_ids)
    # Check that all updates were persisted correctly.
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host, updated_guest_ids)
  end

  it 'should not revoke guest entitlements when guest no longer registered' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, [@uuid2])
    results = @consumer.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
    results.created.size.should == 0
    results.updated.size.should == 1

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host entitlements when guestId list is empty' do
    @host_client.list_entitlements.length.should == 1
    # Host reports no guests.
    host_mapping_no_guests = get_host_guest_mapping(@expected_host, [])
    results = @consumer.hypervisor_check_in(@owner['key'],  host_mapping_no_guests)
    results.created.size.should == 0
    results.updated.size.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host and guest entitlements when guestId list is empty' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, [])
    results = @consumer.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
    results.created.size.should == 0
    results.updated.size.should == 1

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should initialize guest ids to empty when creating new host' do
    host_guest_mapping = get_host_guest_mapping(random_string('new_host'), [])
    results = @consumer.hypervisor_check_in(@owner['key'], host_guest_mapping)
    # Host consumer should have been created.
    results.created.size.should == 1
    results.created[0]['guestIds'].should_not == nil
  end

  it 'should not really support multiple orgs reporting the same cluster' do
    owner1 = create_owner(random_string('owner1'))
    owner2 = create_owner(random_string('owner2'))
    user1 = user_client(owner1, random_string('username1'))
    user2 = user_client(owner2, random_string('username2'))
    consumer1 = consumer_client(user1, random_string('consumer1'))
    consumer2 = consumer_client(user2, random_string('consumer2'))
    hostname = random_string('new_host')
    host_guest_mapping = get_host_guest_mapping(hostname, ["guest1", "guest2", "guest3"])
    # Check in each org
    results1 = consumer1.hypervisor_check_in(owner1['key'], host_guest_mapping)
    results2 = consumer2.hypervisor_check_in(owner2['key'], host_guest_mapping)
    results1.created.size.should == 1
    results2.created.size.should == 0
    results1.updated.size.should == 0
    results2.updated.size.should == 0
    results1.unchanged.size.should == 0
    results2.unchanged.size.should == 0
    results2.failedUpdate.size == 1
    # Now check in each org again
    results1 = consumer1.hypervisor_check_in(owner1['key'], host_guest_mapping)
    results2 = consumer2.hypervisor_check_in(owner2['key'], host_guest_mapping)
    # Nothing should have changed
    results1.created.size.should == 0
    results2.created.size.should == 0
    results1.updated.size.should == 0
    results2.updated.size.should == 0
    results1.unchanged.size.should == 1
    results2.unchanged.size.should == 0
    results2.failedUpdate.size.should == 1
    # Send modified data for owner 1, but it shouldn't impact owner 2 at all
    new_host_guest_mapping = get_host_guest_mapping(hostname, ["guest1", "guest2", "guest3", "guest4"])
    results1 = consumer1.hypervisor_check_in(owner1['key'], new_host_guest_mapping)
    results2 = consumer2.hypervisor_check_in(owner2['key'], host_guest_mapping)
    # Now owner 1 should have an update, but owner two should remain the same
    results1.created.size.should == 0
    results2.created.size.should == 0
    results1.updated.size.should == 1
    results2.updated.size.should == 0
    results1.unchanged.size.should == 0
    results2.unchanged.size.should == 0
    results2.failedUpdate.size.should == 1
  end

  def get_host_guest_mapping(host_uuid, guest_id_list)
    return { host_uuid => guest_id_list }
  end

  def check_hypervisor_consumer(consumer, expected_host_uuid, expected_guest_ids)
    consumer['name'].should == expected_host_uuid

    guest_ids = consumer['guestIds']
    guest_ids.size.should == expected_guest_ids.size

    # sort the ids to make sure that we have the same list.
    sorted_ids = guest_ids.sort { |a, b| a['guestId'] <=> b['guestId'] }
    sorted_expected = expected_guest_ids.sort

    (0..sorted_ids.size - 1).each do |i|
	    sorted_ids[i]['guestId'].should == sorted_expected[i]
    end
  end

  # Tests a scenario where permissions were blocking update:
  it 'should allow virt-who to update mappings' do
    virtwho1 = create_virtwho_client(@user)
    host_guest_mapping = get_host_guest_mapping(random_string('my-host'), ['g1', 'g2'])
    results = virtwho1.hypervisor_check_in(@owner['key'], host_guest_mapping)
    results.should_not be_nil
    results.created.size.should == 1

    results = virtwho1.hypervisor_check_in(@owner['key'], host_guest_mapping)
    results.should_not be_nil
    results.unchanged.size.should == 1
  end

  it 'should block virt-who if owner does not match identity cert' do
    virtwho1 = create_virtwho_client(@user)
    host_guest_mapping = get_host_guest_mapping(random_string('my-host'), ['g1', 'g2'])
    lambda do
      results = virtwho1.hypervisor_check_in(@owner2['key'], host_guest_mapping)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  def create_virtwho_client(user)
    consumer = user.register(random_string("virt-who"), :system, nil, {},
        nil, nil, [], [{:productId => 'installedprod',
           :productName => "Installed"}])
    consumer_api = Candlepin.new(username=nil, password=nil,
                                  cert=consumer['idCert']['cert'],
                                  key=consumer['idCert']['key'])
    return consumer_api
  end
end
