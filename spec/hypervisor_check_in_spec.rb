require 'candlepin_scenarios'
require 'virt_fixture'

describe 'Hypervisor Resource' do

  include CandlepinMethods
  include CandlepinScenarios
  include VirtFixture

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?
    @expected_host = random_string("Host1")
    @expected_guest_ids = [@uuid1, @uuid2]

    # Check in with initial hypervisor to create host consumer and associate guests.
    host_guest_mapping = get_host_guest_mapping(@expected_host, @expected_guest_ids)
    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    results.created.size.should == 1

    consumer = results.created[0]
    check_hypervisor_consumer(consumer, @expected_host, @expected_guest_ids)

    @cp.get_consumer_guests(@expected_host).length.should == 2
    @host_client = registered_consumer_client(consumer)
    @host_client.consume_pool(@virt_limit_pool['id'])
  end

  it 'should add host and associate guests' do
    consumer = @cp.get_consumer(@expected_host)
    check_hypervisor_consumer(consumer, @expected_host, @expected_guest_ids)
  end

  it 'should update host guest ids' do
    # Update the guest ids
    new_guest_id = 'Guest3'
    updated_guest_ids = [@uuid2, new_guest_id]
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, updated_guest_ids)
    results = @user.hypervisor_check_in(@owner['key'], updated_host_guest_mapping)
    # Host consumer already existed, no creation occurred.
    results.created.size.should == 0
    # Check updates.
    results.updated.size.should == 1
    # Ensure that we are returning the updated consumer correctly.
    check_hypervisor_consumer(results.updated[0], @expected_host, updated_guest_ids)
    # Check that all updates were persisted correctly.
    consumer = @cp.get_consumer(@expected_host)
    check_hypervisor_consumer(consumer, @expected_host, updated_guest_ids)
  end

  it 'should not revoke guest entitlements when guest no longer registered' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id)
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, [@uuid2])
    results = @user.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
    results.created.size.should == 0
    results.updated.size.should == 1

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host entitlements when guestId list is empty' do
    @host_client.list_entitlements.length.should == 1
    # Host reports no guests.
    host_mapping_no_guests = get_host_guest_mapping(@expected_host, [])
    results = @user.hypervisor_check_in(@owner['key'],  host_mapping_no_guests)
    results.created.size.should == 0
    results.updated.size.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host and guest entitlements when guestId list is empty' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id)
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host, [])
    results = @user.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
    results.created.size.should == 0
    results.updated.size.should == 1

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should be able to delete and recreate a hypervisor' do

    chuck_owner = create_owner(random_string('chuck'))
    chuck_username = random_string 'chuck'
    alice_username = random_string 'alice'
    chuck_cp = user_client(chuck_owner, chuck_username)
    alice_cp = user_client(@owner, alice_username)


    deletable_uuid = random_string("string-used-as-a-mock-uuid")
    host_guest_mapping = get_host_guest_mapping(deletable_uuid, [])
    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    @cp.unregister(deletable_uuid)
    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    # the update should fail since the consumer got deleted
    results.failedUpdate.size.should == 1
    lambda do
      @cp.get_consumer(deletable_uuid)
    end.should raise_exception(RestClient::Gone)

    # ensure that the owner cannot undo the delete record
    lambda do
      alice_cp.remove_deletion_record(deletable_uuid)
    end.should raise_exception(RestClient::Forbidden)

    # ensure that a random user cannot undo the delete record
    lambda do
      chuck_cp.remove_deletion_record(deletable_uuid)
    end.should raise_exception(RestClient::Forbidden)

    @cp.remove_deletion_record(deletable_uuid)
    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    results.failedUpdate.size.should == 0
  end

  def get_host_guest_mapping(host_uuid, guest_id_list)
    return { host_uuid => guest_id_list }
  end

  def check_hypervisor_consumer(consumer, expected_host_uuid, expected_guest_ids)
    consumer['uuid'].should == expected_host_uuid
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

end
