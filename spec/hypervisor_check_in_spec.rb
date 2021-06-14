require 'spec_helper'
require 'candlepin_scenarios'
require 'thread'

describe 'Hypervisor Resource', :type => :virt do
  include CandlepinMethods
  include VirtHelper
  include AttributeHelper

  before(:each) do
    skip("candlepin running in standalone mode") if is_hosted?
    @expected_host_hyp_id = random_string("host")
    @expected_host_name = random_string("name")
    @expected_guest_ids = [@uuid1, @uuid2]

    # we must register the consumer to use it as a client
    # hypervisor check in creation does not result in a client cert
    consumer = @user.register(@expected_host_name, :hypervisor, nil, {"test_fact" => "fact_value"},
        nil, nil, [], [], nil, [], @expected_host_hyp_id)

    # Check in to associate guests.
    host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, @expected_guest_ids)

    results = @user.hypervisor_check_in(@owner['key'],  host_guest_mapping)
    results.updated.size.should == 1

    @host_uuid = results.updated[0].uuid
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host_name, @expected_guest_ids)

    @cp.get_consumer_guests(@host_uuid).length.should == 2
    @host_client = registered_consumer_client(consumer)
    @host_client.consume_pool(@virt_limit_pool['id'], {:quantity => 1})

    @consumer = consumer_client(@user, random_string("consumer"))

    # For testing some cross-org hypervisor check-ins:
    @owner2 = create_owner random_string('virt_owner2')
    @user2 = user_client(@owner2, random_string('virt_user2'))
  end

  it 'should add consumer to created when new host id and no guests reported' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, [])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_hyp_id,
      host_hyp_id)
  end

  it 'should not deadlock when two synchronous hypervisor checkins are performed' do
    hypervisor_ids = [random_string('host'), random_string('host')]
    # The guest ids that will be reported
    guest_id_lists = [['g1', 'g2'], ['g3', 'g4'], ['g5', 'g6']]
    mapping1 = {hypervisor_ids[0] => guest_id_lists[0], hypervisor_ids[1] => guest_id_lists[0]}
    # Do an initial checkin to ensure the consumers for each hypervisor exists
    # This is done to be more sure of the occurance of a deadlock
    @consumer.hypervisor_check_in(@owner['key'], mapping1)
    # The mappings used to create the deadlock will use different guest ids
    # The first mapping will use guest_id_lists[1]
    # The second will use guest_id_lists[2]
    deadlock_mapping = {hypervisor_ids[0] => guest_id_lists[1], hypervisor_ids[1] => guest_id_lists[1]}
    reversed_deadlock_mapping = {}
    deadlock_mapping.keys.reverse.each {|key| reversed_deadlock_mapping[key] = guest_id_lists[2]}
    completed_queue = Queue.new
    result1 = nil
    result2 = nil
    thread1 = Thread.new {
        result1 = @consumer.hypervisor_check_in(@owner['key'], deadlock_mapping)
        completed_queue << 1
      }
    thread2 = Thread.new {
        result2 = @consumer.hypervisor_check_in(@owner['key'], reversed_deadlock_mapping)
        completed_queue << 2
      }
    thread1.join
    thread2.join
    first_complete = completed_queue.pop  # We do not need to know which was completed first
    second_complete = completed_queue.pop
    # Check that both reports were processed
    # Since we are submitting different guestIds
    # we expect each hypervisor check in to update both hypervisors
    result1.should_not be_nil
    result1.created.size.should == 0
    result1.updated.size.should == 2
    result1.unchanged.size.should == 0
    result1.failedUpdate.size.should == 0
    result2.should_not be_nil
    result2.created.size.should == 0
    result2.updated.size.should == 2
    result2.unchanged.size.should == 0
    result2.failedUpdate.size.should == 0
    # Show that the last report completed is the one that is persisted
    hypervisors = @user.get_owner_hypervisors(@owner['key'])
    guest_ids_to_check = guest_id_lists[second_complete]
    hypervisors.each { |hypervisor|
        # Only check the hypervisors created in this test
        if hypervisor_ids.include? hypervisor.name
                   guest_ids_of_hypervisor = @cp.get_guestids(hypervisor.uuid)
                   guest_ids_of_hypervisor.size.should == guest_ids_to_check.size
                   guest_ids_of_hypervisor.each { |guestId|
                       guest_ids_to_check.should include(guestId.guestId)
                   }
        end
    }
  end

 it 'should add consumer to created when new host id and no guests reported - async' do
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, [])
    result_data = job_detail['resultData']
    hyp_consumer = @cp.get_consumer(result_data.created[0].uuid)
    hyp_consumer.facts['test_fact'].should == 'fact_value'
    should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result_data, host_name,
      host_hyp_id)
  end

  def should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_name,
    host_hyp_id)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0

    result.created[0].name.should == host_name
    result.created[0].owner['key'].should == @owner['key']

    # verify our created consumer is correct.
    created_consumer = @cp.get_consumer(result.created[0].uuid)
    created_consumer.name.should == host_name
    created_consumer.idCert.should be_nil
    # Test get_owner_hypervisors works, should return all
    hypervisors = @user.get_owner_hypervisors(@owner['key'])
    hypervisors.size.should == 2
    # Test lookup with hypervisor ids
    hypervisors = @user.get_owner_hypervisors(@owner['key'], [host_hyp_id])
    hypervisors.size.should == 1
    # Test lookup with nonexistant hypervisor id
    hypervisors = @user.get_owner_hypervisors(@owner['key'], ["probably not a hypervisor"])
    hypervisors.size.should == 0
    # verify last checkin time is updated
    created_consumer.lastCheckin.should_not be_nil
  end


  it 'should add consumer to created when new host id and guests were reported' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, ['g1'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_created_when_new_host_id_and_guests_were_reported(result, host_hyp_id)
  end

  it 'should add consumer to created when new host id and guests were reported - async' do
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, ['g1'])
    result_data = job_detail['resultData']
    should_add_consumer_to_created_when_new_host_id_and_guests_were_reported(result_data, host_name)
  end

  def should_add_consumer_to_created_when_new_host_id_and_guests_were_reported(result, host_name)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0

    result.created[0].name.should == host_name
    result.created[0].owner['key'].should == @owner['key']

    created_consumer =  @cp.get_consumer(result.created[0].uuid)
    # verify our created consumer is correct.
    created_consumer.name.should == host_name
    # verify last checkin time is updated
    created_consumer.lastCheckin.should_not be_nil
  end

  it 'should not add new consumer when create_missing is false' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, ['g1'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping, false)
    should_not_add_new_consumer_when_create_missing_is_false(result)
  end

  it 'should not add new consumer when create_missing is false - async' do
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, ['g1'], false)
    result_data = job_detail['resultData']
    should_not_add_new_consumer_when_create_missing_is_false(result_data)
  end

  def should_not_add_new_consumer_when_create_missing_is_false(result)
    # Should only  have a result entry for failed.
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 1
  end

  it 'should add consumer to updated when guest ids are updated' do
    mapping = get_host_guest_mapping(@expected_host_hyp_id, ['g1', 'g2'])
    old_check_in = @cp.get_consumer(@host_uuid)
    #because mysql
    sleep 2
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_updated_when_guest_ids_are_updated(result, old_check_in)
  end

  it 'should add consumer to updated when guest ids are updated - async' do
    old_check_in = @cp.get_consumer(@host_uuid)
    #because mysql
    sleep 2
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id,  ['g1', 'g2'])
    result_data = job_detail['resultData']
    should_add_consumer_to_updated_when_guest_ids_are_updated(result_data, old_check_in)
  end

  def should_add_consumer_to_updated_when_guest_ids_are_updated(result, old_check_in)
    # Should only  have a result entry for updated.
    result.created.size.should == 0
    result.updated.size.should == 1
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0

    result.updated[0].name.should == @expected_host_name
    result.updated[0].owner['key'].should == @owner['key']

    # verify our created consumer is correct.
    updated_consumer = @cp.get_consumer(result.updated[0].uuid)
    updated_consumer.name.should == @expected_host_name
    # verify last checkin time is updated
    last_check_in = updated_consumer.lastCheckin
    last_check_in.should_not be_nil
    last_check_in.should_not == old_check_in
  end

  it 'should add consumer to unchanged when same guest ids are sent' do
    mapping = get_host_guest_mapping(@expected_host_hyp_id, @expected_guest_ids)
    old_check_in = @cp.get_consumer(@host_uuid)
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result, old_check_in)
  end

  it 'should add consumer to unchanged when same guest ids are sent - async' do
    old_check_in = @cp.get_consumer(@host_uuid)
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, @expected_guest_ids)
    result_data = job_detail['resultData']
    should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result_data, old_check_in)
  end

  def should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result, old_check_in)
    # Should only  have a result entry for unchanged.
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0

    result.unchanged[0].name.should == @expected_host_name
    result.unchanged[0].owner['key'].should == @owner['key']

    unchanged_consumer = @cp.get_consumer(result.unchanged[0].uuid)
    # verify our created consumer is correct.
    unchanged_consumer.name.should == @expected_host_name
    # verify last checkin time is updated
    last_check_in = unchanged_consumer.lastCheckin
    last_check_in.should_not be_nil
    last_check_in.should_not == old_check_in
  end

  it 'should add consumer to unchanged when comparing empty guest id lists' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, [])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    result.created.size.should == 1
    result.created[0].name.should == host_hyp_id

    # Do the same update with [] and it should be considered unchanged.
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_unchanged_when_comparing_empty_guest_id_lists(result, host_hyp_id)
  end

  it 'should add consumer to unchanged when comparing empty guest id lists - async' do
    host_hyp_id = random_string('host')
    host_name = random_string('host')
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, [])
    result_data = job_detail['resultData']
    result_data.created.size.should == 1
    created_consumer = @cp.get_consumer(result_data.created[0].uuid)
    created_consumer.name.should == host_name

    # Do the same update with [] and it should be considered unchanged.
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, [])
    result_data = job_detail['resultData']
    should_add_consumer_to_unchanged_when_comparing_empty_guest_id_lists(result_data, host_name)
  end

  def should_add_consumer_to_unchanged_when_comparing_empty_guest_id_lists(result, host_name)
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0

    result.unchanged[0].name.should == host_name
    result.unchanged[0].owner['key'].should == @owner['key']

    unchanged_consumer = @cp.get_consumer(result.unchanged[0].uuid)
    # verify our unchanged consumer is correct.
    unchanged_consumer.name.should == host_name
  end

 it 'should add host and associate guests' do
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host_name, @expected_guest_ids)
  end

  it 'should update host guest ids as consumer' do
    update_guest_ids_test(@consumer)
  end

  it 'should update host guest ids as consumer - async' do
    async_update_guest_ids_test(@consumer)
  end

  it 'should persist reporter id on host guest mappings update - async' do
    async_update_guest_ids_test(@consumer, 'Lois Lane')
  end

  it 'should update host guest ids as user' do
    update_guest_ids_test(@user)
  end

  it 'should update host guest ids as user - async' do
    async_update_guest_ids_test(@user)
  end

  it 'should not revoke guest entitlements when guest no longer registered' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, [@uuid2])
    results = @consumer.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
    should_not_revoke_guest_entitlements_when_guest_no_longer_registered(results)
  end

  it 'should not revoke guest entitlements when guest no longer registered - async' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, [@uuid2])
    result_data = job_detail['resultData']
    should_not_revoke_guest_entitlements_when_guest_no_longer_registered(result_data)
  end

  def should_not_revoke_guest_entitlements_when_guest_no_longer_registered(results)
    results.created.size.should == 0
    results.updated.size.should == 1

    results.updated[0].name.should == @expected_host_name
    results.updated[0].owner['key'].should == @owner['key']

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host entitlements when guestId list is empty' do
    @host_client.list_entitlements.length.should == 1
    # Host reports no guests.
    host_mapping_no_guests = get_host_guest_mapping(@expected_host_hyp_id, [])
    results = @consumer.hypervisor_check_in(@owner['key'],  host_mapping_no_guests)
    should_not_revoke_host_entitlements_when_guestId_list_is_empty(results)
  end

  it 'should not revoke host entitlements when guestId list is empty - async' do
    @host_client.list_entitlements.length.should == 1
    # Host reports no guests.
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, [])
    result_data = job_detail['resultData']
    should_not_revoke_host_entitlements_when_guestId_list_is_empty(result_data)
  end

  def should_not_revoke_host_entitlements_when_guestId_list_is_empty(result)
    result.created.size.should == 0
    result.updated.size.should == 1

    result.updated[0].name.should == @expected_host_name
    result.updated[0].owner['key'].should == @owner['key']

    @host_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host and guest entitlements when guestId list is empty' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)
    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, [])
    results = @consumer.hypervisor_check_in(@owner['key'], updated_host_guest_mapping)
    should_not_revoke_host_and_guest_entitlements_when_guestId_list_is_empty(results)
  end

  it 'should not revoke host and guest entitlements when guestId list is empty - async' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)
    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, [])
    result_data = job_detail['resultData']
    should_not_revoke_host_and_guest_entitlements_when_guestId_list_is_empty(result_data)
  end

  def should_not_revoke_host_and_guest_entitlements_when_guestId_list_is_empty(result)
    result.created.size.should == 0
    result.updated.size.should == 1

    result.updated[0].name.should == @expected_host_name
    result.updated[0].owner['key'].should == @owner['key']

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should initialize guest ids to empty when creating new host' do
    host_guest_mapping = get_host_guest_mapping(random_string('host'), [])
    results = @consumer.hypervisor_check_in(@owner['key'], host_guest_mapping)
    # Host consumer should have been created.
    results.created.size.should == 1
    @cp.get_guestids(results.created[0].uuid).should_not == nil
  end

  it 'should initialize guest ids to empty when creating new host - async' do
    job_detail = async_update_hypervisor(@owner, @consumer, random_string('name'), random_string('host'), [])
    result_data = job_detail['resultData']
    # Host consumer should have been created.
    result_data.created.size.should == 1
    @cp.get_guestids(result_data.created[0].uuid).should_not == nil
  end

  it 'should support multiple orgs reporting the same cluster' do
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
    multiple_orgs_check_1(results1, results2)
    # Now check in each org again
    results1 = consumer1.hypervisor_check_in(owner1['key'], host_guest_mapping)
    results2 = consumer2.hypervisor_check_in(owner2['key'], host_guest_mapping)
    # Nothing should have changed
    multiple_orgs_check_2(results1, results2)
    # Send modified data for owner 1, but it shouldn't impact owner 2 at all
    new_host_guest_mapping = get_host_guest_mapping(hostname, ["guest1", "guest2", "guest3", "guest4"])
    results1 = consumer1.hypervisor_check_in(owner1['key'], new_host_guest_mapping)
    results2 = consumer2.hypervisor_check_in(owner2['key'], host_guest_mapping)
    # Now owner 1 should have an update, but owner two should remain the same
    multiple_orgs_check_3(results1, results2)
  end

  it 'should support multiple orgs reporting the same cluster - async' do
    owner1 = create_owner(random_string('owner1'))
    owner2 = create_owner(random_string('owner2'))
    user1 = user_client(owner1, random_string('username1'))
    user2 = user_client(owner2, random_string('username2'))
    consumer1 = consumer_client(user1, random_string('consumer1'))
    consumer2 = consumer_client(user2, random_string('consumer2'))
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    first_guest_list = ["guest1", "guest2", "guest3"]
    second_guest_list = ["guest1", "guest2", "guest3", "guest4"]
    job_detail1 = async_update_hypervisor(owner1, consumer1, host_name, host_hyp_id, first_guest_list)
    results1 = job_detail1['resultData']
    job_detail2 = async_update_hypervisor(owner2, consumer2, host_name, host_hyp_id, first_guest_list)
    results2 = job_detail2['resultData']
    # Check in each org
    multiple_orgs_check_1(results1, results2)
    # Now check in each org again
    job_detail1 = async_update_hypervisor(owner1, consumer1, host_name, host_hyp_id, first_guest_list)
    results1 = job_detail1['resultData']
    job_detail2 = async_update_hypervisor(owner2, consumer2, host_name, host_hyp_id, first_guest_list)
    results2 = job_detail2['resultData']
    # Nothing should have changed
    multiple_orgs_check_2(results1, results2)
    # Send modified data for owner 1, but it shouldn't impact owner 2 at all
    job_detail1 = async_update_hypervisor(owner1, consumer1, host_name, host_hyp_id, second_guest_list)
    results1 = job_detail1['resultData']
    job_detail2 = async_update_hypervisor(owner2, consumer2, host_name, host_hyp_id, first_guest_list)
    results2 = job_detail2['resultData']
    # Now owner 1 should have an update, but owner two should remain the same
    multiple_orgs_check_3(results1, results2)
  end

  def multiple_orgs_check_1(result_1, result_2)
    result_1.created.size.should == 1
    result_2.created.size.should == 1
    result_1.updated.size.should == 0
    result_2.updated.size.should == 0
    result_1.unchanged.size.should == 0
    result_2.unchanged.size.should == 0
    result_2.failedUpdate.size == 0
  end

  def multiple_orgs_check_2(result_1, result_2)
    result_1.created.size.should == 0
    result_2.created.size.should == 0
    result_1.updated.size.should == 0
    result_2.updated.size.should == 0
    result_1.unchanged.size.should == 1
    result_2.unchanged.size.should == 1
    result_2.failedUpdate.size.should == 0
  end

  def multiple_orgs_check_3(result_1, result_2)
    result_1.created.size.should == 0
    result_2.created.size.should == 0
    result_1.updated.size.should == 1
    result_2.updated.size.should == 0
    result_1.unchanged.size.should == 0
    result_2.unchanged.size.should == 1
    result_2.failedUpdate.size.should == 0
  end

  # Tests a scenario where permissions were blocking update:
  it 'should allow virt-who to update mappings' do ##
    virtwho1 = create_virtwho_client(@user)
    host_guest_mapping = get_host_guest_mapping(random_string('my-host'), ['g1', 'g2'])
    results = virtwho1.hypervisor_check_in(@owner['key'], host_guest_mapping)
    results.should_not be_nil
    results.created.size.should == 1

    results = virtwho1.hypervisor_check_in(@owner['key'], host_guest_mapping)
    results.should_not be_nil
    results.unchanged.size.should == 1
  end

  it 'should allow virt-who to update mappings - async' do
    virtwho1 = create_virtwho_client(@user)
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    job_detail = async_update_hypervisor(@owner, virtwho1, host_name, host_hyp_id, ['g1', 'g2'])
    job_detail['resultData'].created.size.should == 1

    job_detail['resultData'].created[0].name.should == host_name
    job_detail['resultData'].created[0].owner['key'].should == @owner['key']

    job_detail = async_update_hypervisor(@owner, virtwho1, host_name, host_hyp_id, ['g1', 'g2'])
    job_detail['resultData'].unchanged.size.should == 1

    job_detail['resultData'].unchanged[0].name.should == host_name
    job_detail['resultData'].unchanged[0].owner['key'].should == @owner['key']
  end

  it 'should block virt-who if owner does not match identity cert' do
    virtwho1 = create_virtwho_client(@user)
    host_guest_mapping = get_host_guest_mapping(random_string('my-host'), ['g1', 'g2'])
    lambda do
      results = virtwho1.hypervisor_check_in(@owner2['key'], host_guest_mapping)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should block virt-who if owner does not match identity cert - async' do
    virtwho1 = create_virtwho_client(@user)
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    guests = ['g1', 'g2']

    host_mapping = get_async_host_guest_mapping(host_name, host_hyp_id, guests)
    lambda do
      job_detail = JSON.parse(virtwho1.hypervisor_update(@owner2['key'], host_mapping))
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should raise bad request exception if mapping was not provided' do
    virtwho = create_virtwho_client(@user)
    lambda do
      virtwho.hypervisor_check_in(@owner['key'], nil)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should raise bad request exception if mapping was not provided - async' do
    virtwho = create_virtwho_client(@user)
    lambda do
      virtwho.hypervisor_update(@owner['key'], nil)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should raise bad request exception if invalid mapping input was provided - async' do
    virtwho = create_virtwho_client(@user)
    lambda do
      virtwho.hypervisor_update(@owner['key'], 'test invalid input')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should see the capability that corresponds to the async method' do
    json = @cp.get_status()
    json['managerCapabilities'].should include("hypervisors_async")
  end

  it 'should ignore hypervisorIds equal to the empty string' do
    virtwho = create_virtwho_client(@user)
    host_hyp_id = ""
    guests = ['g1', 'g2']
    hostguestmapping = get_host_guest_mapping(host_hyp_id, guests)
    result = virtwho.hypervisor_check_in(@owner['key'], hostguestmapping)
    result.should_not be_nil
    result.each do |k,v|
      v.should be_empty
    end
  end

  it 'should ignore hypervisorIds equal to the empty string - async' do
    virtwho = create_virtwho_client(@user)
    name = random_string("host")
    host_hyp_id = ""
    guests = ['g1', 'g2']
    result = async_update_hypervisor(@owner, virtwho, name, host_hyp_id, guests)
    result.should_not be_nil
    ['updated', 'created', 'unchanged', 'failedUpdate'].each do |key|
      result['resultData'][key].should be_empty
    end
  end

  it 'should ignore guestIds equal to the empty string' do
    virtwho = create_virtwho_client(@user)
    expected_guest_id = random_string('guest')
    guests = [expected_guest_id, '']
    hostguestmapping = get_host_guest_mapping(@expected_host_hyp_id, guests)
    result = virtwho.hypervisor_check_in(@owner['key'], hostguestmapping)
    result.should_not be_nil
    expect(result['updated'].length).to eq(1)
    updated_consumer = result['updated'][0]
    guestIds = @cp.get_guestids(updated_consumer['uuid'])
    expect(guestIds.length).to eq(1)
    guest_id = guestIds[0]['guestId']
    expect(guest_id).to eq(expected_guest_id)
  end

  it 'should ignore guestIds equal to the empty string - async' do
    name = random_string("host")
    virtwho = create_virtwho_client(@user)
    expected_guest_id = random_string('guest')
    guests = [expected_guest_id, '']
    hostguestmapping = get_host_guest_mapping(@expected_host_hyp_id, guests)
    result = async_update_hypervisor(@owner, virtwho, name, @expected_host_hyp_id, guests)
    result.should_not be_nil
    expect(result['resultData']['updated'].length).to eq(1)
    updated_consumer_uuid = result['resultData']['updated'][0].uuid
    guestIds = @cp.get_guestids(updated_consumer_uuid)
    expect(guestIds.length).to eq(1)
    guest_id = guestIds[0]['guestId']
    expect(guest_id).to eq(expected_guest_id)
  end

  def get_host_guest_mapping(host_hyp_id, guest_id_list)
    return { host_hyp_id => guest_id_list }
  end

  def get_async_host_guest_mapping(name, hypervisor_id, guest_id_list, facts=nil)
      guestIds = []
      guest_id_list.each do |guest|
          guestIds << {'guestId' => guest}
      end
      facts = facts || {"test_fact" => "fact_value"}
      json = {
          "hypervisors" => [
              {
                  "name" => name,
                  "hypervisorId" => {"hypervisorId" => hypervisor_id},
                  "guestIds" => guestIds,
                  "facts" => facts
              }
          ]
      }
      return json.to_json
  end

  def update_guest_ids_test(client)
    # Update the guest ids
    new_guest_id = 'Guest3'
    updated_guest_ids = [@uuid2, new_guest_id]
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, updated_guest_ids)
    results = client.hypervisor_check_in(@owner['key'], updated_host_guest_mapping)
    update_guest_ids_test_check(results, updated_guest_ids)
  end

  def async_update_guest_ids_test(client, reporter_id=nil)
    # Update the guest ids
    new_guest_id = 'Guest3'
    updated_guest_ids = [@uuid2, new_guest_id]
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, updated_guest_ids, true, reporter_id)
    result_data = job_detail['resultData']
    update_guest_ids_test_check(result_data, updated_guest_ids, reporter_id)
  end

  def update_guest_ids_test_check(result, updated_guest_ids, reporter_id=nil)
    # Host consumer already existed, no creation occurred.
    result.created.size.should == 0
    # Check that all updates were persisted correctly.
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host_name, updated_guest_ids, reporter_id)
  end

  def check_hypervisor_consumer(consumer, expected_host_name, expected_guest_ids, reporter_id=nil)
    consumer['name'].should == expected_host_name

    guest_ids = @cp.get_guestids(consumer['uuid'])
    # guest_ids = consumer['guestIds']
    guest_ids.size.should == expected_guest_ids.size

    # sort the ids to make sure that we have the same list.
    sorted_ids = guest_ids.sort { |a, b| a['guestId'] <=> b['guestId'] }
    sorted_expected = expected_guest_ids.sort

    (0..sorted_ids.size - 1).each do |i|
        sorted_ids[i]['guestId'].should == sorted_expected[i]
    end
    unless reporter_id.nil?
      reporter_id.should == consumer.hypervisorId.reporterId
    end
  end

  def run_async_update(owner, consumer, host_name, host_hyp_id, guests, create=true, reporter_id=nil, facts=nil)
    host_mapping = get_async_host_guest_mapping(host_name, host_hyp_id, guests, facts)
    job_detail = JSON.parse(consumer.hypervisor_update(owner['key'], host_mapping, create, reporter_id))
    wait_for_job(job_detail['id'], 60)
    return @cp.get_job(job_detail['id'], true)
  end

  def async_update_hypervisor(owner, consumer, host_name, host_hyp_id, guests, create=true, reporter_id=nil, facts=nil)
    job_detail = run_async_update(owner, consumer, host_name, host_hyp_id, guests, create, reporter_id, facts)

    expect(job_detail['state']).to eq('FINISHED')
    expect(job_detail['resultData']).to_not be_nil

    return job_detail
  end

  def send_host_guest_mapping(owner, client, mapping, create=true, reporter_id=nil)
    job_detail = JSON.parse(client.hypervisor_update(owner['key'], mapping, create, reporter_id))
    wait_for_job(job_detail['id'], 60)
    return @cp.get_job(job_detail['id'], true)
  end

  def create_virtwho_client(user)
    consumer = user.register(random_string("virt-who"), :system, nil, {},
        nil, nil, [], [{:productId => 'installedprod', :productName => "Installed"}])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    return consumer_client
  end

  it 'should allow a single guest to be migrated and revoke host-limited ents' do
    owner = create_owner random_string('test_owner1')
    user = user_client(owner, random_string("user"))
    virtwho = create_virtwho_client(user)
    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]

    host_consumer = user.register(random_string('host1'), :hypervisor, nil,
      {}, nil, owner['key'], [], [], nil, [], 'hypervisor_id_1')
    new_host_consumer = user.register(random_string('host2'), :hypervisor, nil,
      {}, nil, owner['key'], [], [], nil, [], 'hypervisor_id_2')

    guest_consumer = user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, owner['key'], [], [])
    # Create a product/subscription
    super_awesome = create_product(nil, random_string('super_awesome'),
                            :attributes => { "virt_limit" => "10", "host_limited" => "true" }, :owner => owner['key'])
    create_pool_and_subscription(owner['key'], super_awesome.id, 20)
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    new_consumer_client = Candlepin.new(nil, nil, new_host_consumer['idCert']['cert'], new_host_consumer['idCert']['key'])
    guest_client = Candlepin.new(nil, nil, guest_consumer['idCert']['cert'], guest_consumer['idCert']['key'])

    virtwho.hypervisor_check_in(owner['key'], get_host_guest_mapping('hypervisor_id_1', [uuid1]))

    # consumer_client.update_guestids(guests)
    pools = consumer_client.list_pools :consumer => host_consumer['uuid']
    pools.length.should == 1
    consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})
    new_consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})

    pools = guest_client.list_pools :consumer => guest_consumer['uuid']

    # The original pool and the new host limited pool should be available
    pools.length.should == 2
    # Get the guest pool
    guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

    # Make sure the required host is actually the host
    requires_host = get_attribute_value(guest_pool['attributes'], "requires_host")
    expect(requires_host).to eq(host_consumer['uuid'])

    # Consume the host limited pool
    guest_client.consume_pool(guest_pool['id'], {:quantity => 1})

    # Should have a host with 1 registered guest
    guestList = @cp.get_consumer_guests(host_consumer['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer['uuid']

    guest_client.list_entitlements().length.should == 1
    # Updating to a new host should remove host specific entitlements

    sleep 2
    virtwho.hypervisor_check_in(owner['key'], get_host_guest_mapping('hypervisor_id_2', [uuid1]))

    # The guests host limited entitlement should be gone
    guest_client.list_entitlements().length.should == 0
  end

  it 'should allow a single guest to be migrated and revoke host-limited ents - async' do
    owner = create_owner random_string('test_owner1')
    user = user_client(owner, random_string("user"))
    virtwho = create_virtwho_client(user)
    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]

    host_consumer = user.register(random_string('host1'), :hypervisor, nil,
      {}, nil, owner['key'], [], [], nil, [], 'hypervisor_id_1')
    new_host_consumer = user.register(random_string('host2'), :system, nil,
      {}, nil, owner['key'], [], [], nil, [], 'hypervisor_id_2')

    guest_consumer = user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, owner['key'], [], [])
    # Create a product/subscription
    super_awesome = create_product(nil, random_string('super_awesome'),
                            :attributes => { "virt_limit" => "10", "host_limited" => "true" }, :owner => owner['key'])
    create_pool_and_subscription(owner['key'], super_awesome.id, 20)
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    new_consumer_client = Candlepin.new(nil, nil, new_host_consumer['idCert']['cert'], new_host_consumer['idCert']['key'])
    guest_client = Candlepin.new(nil, nil, guest_consumer['idCert']['cert'], guest_consumer['idCert']['key'])

    async_update_hypervisor(owner, user, 'tester', 'hypervisor_id_1', [uuid1])

    # consumer_client.update_guestids(guests)
    pools = consumer_client.list_pools :consumer => host_consumer['uuid']
    pools.length.should == 1
    consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})
    new_consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})

    pools = guest_client.list_pools :consumer => guest_consumer['uuid']

    # The original pool and the new host limited pool should be available
    pools.length.should == 2
    # Get the guest pool
    guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

    # Make sure the required host is actually the host
    requires_host = get_attribute_value(guest_pool['attributes'], "requires_host")
    expect(requires_host).to eq(host_consumer['uuid'])

    # Consume the host limited pool
    guest_client.consume_pool(guest_pool['id'], {:quantity => 1})

    # Should have a host with 1 registered guest
    guestList = @cp.get_consumer_guests(host_consumer['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer['uuid']

    guest_client.list_entitlements().length.should == 1
    # Updating to a new host should remove host specific entitlements

    async_update_hypervisor(owner, user, 'tester', 'hypervisor_id_2', [uuid1])

    # The guests host limited entitlement should be gone
    guest_client.list_entitlements().length.should == 0
  end

  it 'should allow existing guest to be migrated to an existing host - async' do
    owner = create_owner random_string('test_owner1')
    user = user_client(owner, random_string("user"))
    virtwho = create_virtwho_client(user)

    host1_name = random_string('host1')

    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]
    guest_id_to_migrate = random_string('system.uuid')

    before_migration = {
        "hypervisors" => [
            {
                "name" => 'host_1',
                "hypervisorId" => {"hypervisorId" => 'hypervisor_id_1'},
                "guestIds" => [
                    {'guestId' => uuid1}
                ],
                "facts" => {"test_fact" => "fact_value" }
            },
            {
                "name" => "host_2",
                "hypervisorId" => {"hypervisorId" => 'hypervisor_id_2'},
                "guestIds" => [
                    {'guestId' => guest_id_to_migrate}
                ],
                "facts" => {"test_fact" => "fact_value" }
            }
        ]
    }

    job_detail = send_host_guest_mapping(owner, user, before_migration.to_json())
    expect(job_detail["state"]).to eq("FINISHED")

    after_migration = {
        "hypervisors" => [
            {
                "name" => host1_name,
                "hypervisorId" => {"hypervisorId" => 'hypervisor_id_1'},
                "guestIds" => [
                    {'guestId' => uuid1},
                    {'guestId' => guest_id_to_migrate}
                ],
                "facts" => {"test_fact" => "fact_value" }
            },
            {
                "name" => "host_2",
                "hypervisorId" => {"hypervisorId" => 'hypervisor_id_2'},
                "guestIds" => [

                ],
                "facts" => {"test_fact" => "fact_value" }
            }
        ]
    }

    job_detail = send_host_guest_mapping(owner, user, after_migration.to_json())
    expect(job_detail["state"]).to eq("FINISHED")
  end

  it 'Hypervisor Checkin should complete succesfully when a guest with host specific entitlement is migrated' do
    owner_key = random_string('test_owner')
    owner = create_owner owner_key

    prod = create_product('taylorid', 'taylor swift', {:owner => owner_key, :version => "6.1"})
    prod1 = create_product(nil, nil, {:owner => owner_key, :attributes => {
      :stacking_id => "ouch",
      "virt_limit" => 1,
      "sockets" => 1,
      "instance_multiplier" => 2,
      "multi-entitlement" => "yes",
      "host_limited" => "true"},
      :providedProducts => [prod.id]})
    create_pool_and_subscription(owner['key'], prod1['id'], 10, [prod['id']])

    guest_facts = {
      "virt.is_guest"=>"true",
      "virt.uuid"=>"myGuestId",
      "system.certificate_version"=>"3.2"
    }
    guest = @cp.register('guest.bind.com',:system, nil, guest_facts, 'admin',
      owner_key, [], [{"productId" => prod.id, "productName" => "taylor swift"}])
    hypervisor_facts = {
      "virt.is_guest"=>"false"
    }
    hypervisor_guests = [{"guestId"=>"myGuestId"}]
    hypervisor_id = random_string('hypervisorid')
    hypervisor = @cp.register('hypervisor.bind.com',:system, nil, hypervisor_facts, 'admin',
      owner_key, [], [{"productId" => prod.id, "productName" => "taylor swift"}], nil, [], hypervisor_id)
    hypervisor = @cp.update_consumer({:uuid => hypervisor.uuid, :guestIds => hypervisor_guests})
    @cp.consume_product(nil, {:uuid => guest.uuid})
    @cp.list_entitlements({:uuid => guest.uuid}).length.should == 1
    user = user_client(owner, random_string("user"))
    job_detail = async_update_hypervisor(owner, user, 'hypervisor.bind.com', hypervisor_id, ["blah"])
  end

  it 'should merge consumer into hypervisor with the same uuid' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    virtwho = create_virtwho_client(user)
    host_hyp_id = "test-uuid"
    guests = ['g1', 'g2']
    hostguestmapping = get_host_guest_mapping(host_hyp_id, guests)
    result = virtwho.hypervisor_check_in(owner['key'], hostguestmapping)
    result.should_not be_nil

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, owner['key'])

    @cp.list_consumers({:owner=>owner['key']}).length.should == 2
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    result['created'][0]['uuid'].should == test_host['uuid']
  end

  it 'should merge consumer that does not specify owner key into hypervisor with the same uuid' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    virtwho = create_virtwho_client(user)
    host_hyp_id = "test-uuid"
    guests = ['g1', 'g2']
    hostguestmapping = get_host_guest_mapping(host_hyp_id, guests)
    result = virtwho.hypervisor_check_in(owner['key'], hostguestmapping)
    result.should_not be_nil

    # NOTE: we don't specify owner key during registration so that candlepin will have to resolve the owner
    # based on the user principal:
    test_host = user.register("test-host", :system, nil,
      {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, nil)

    @cp.list_consumers({:owner=>owner['key']}).length.should == 2
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    result['created'][0]['uuid'].should == test_host['uuid']
  end

  it 'should merge consumer into hypervisor with the same uuid - async' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id = "test-uuid"
    guests = ['g1', 'g2']
    job_detail = async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests)
    result_data = job_detail['resultData']
    result_data.created.size.should == 1
    hypervisor_uuid = result_data.created[0].uuid

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, owner['key'])

    @cp.list_consumers({:owner=>owner['key']}).length.should == 1
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    hypervisor_uuid.should == test_host['uuid']
  end

  it 'should merge hypervisor into consumer with the same uuid' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, owner['key'])

    virtwho = create_virtwho_client(user)
    host_hyp_id = "test-uuid"
    guests = ['g1', 'g2']
    hostguestmapping = get_host_guest_mapping(host_hyp_id, guests)
    result = virtwho.hypervisor_check_in(owner['key'], hostguestmapping)
    result.should_not be_nil

    @cp.list_consumers({:owner=>owner['key']}).length.should == 2
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    result['updated'][0]['uuid'].should == test_host['uuid']
  end

  it 'should merge hypervisor into consumer with the same uuid - async' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, owner['key'])

    host_hyp_id = "test-uuid"
    guests = ['g1', 'g2']
    job_detail = async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests)
    result_data = job_detail['resultData']
    result_data.updated.size.should == 1
    hypervisor_uuid = result_data.updated[0].uuid

    @cp.list_consumers({:owner=>owner['key']}).length.should == 1
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    hypervisor_uuid.should == test_host['uuid']
  end

  it 'should merge hypervisor into consumer with the same uuid ignore casing - async' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "TEST-UUID", "virt.is_guest"=>"false"}, nil, owner['key'])

    host_hyp_id = "hypervisor"
    guests = ['g1', 'g2']
    host_facts = {
        "dmi.system.uuid" => "test-uuid"}
    job_detail = async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests, nil, nil, host_facts)
    result_data = job_detail['resultData']
    result_data.updated.size.should == 1
    hypervisor_uuid = result_data.updated[0].uuid

    @cp.list_consumers({:owner=>owner['key']}).length.should == 1
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    hypervisor_uuid.should == test_host['uuid']
  end

  it 'should merge hypervisor into consumer with the same uuid ignore casing reverse - async' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    test_host = user.register("test-host", :system, nil, {"dmi.system.uuid" => "test-uuid", "virt.is_guest"=>"false"}, nil, owner['key'])

    host_hyp_id = "hypervisor"
    guests = ['g1', 'g2']
    host_facts = {
        "dmi.system.uuid" => "TEST-UUID"}
    job_detail = async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests, nil, nil, host_facts)
    result_data = job_detail['resultData']
    result_data.updated.size.should == 1
    hypervisor_uuid = result_data.updated[0].uuid

    @cp.list_consumers({:owner=>owner['key']}).length.should == 1
    test_host = @cp.get_consumer(test_host['uuid'])
    test_host['type']['label'].should == 'hypervisor'
    hypervisor_uuid.should == test_host['uuid']
  end

  it 'should not fail when facts change but not the guest list' do
    # test for BZ 1651651
    # if facts or hypervisor id changes, the migration was attempted
    # leading to a job killing exception
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id1 = random_string("test-uuid")
    guest_set = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests = ['g1', 'g2']

    test_host1 = user.register(host_hyp_id1, :system, nil, {"dmi.system.uuid" => host_hyp_id1, "virt.is_guest"=>"false"}, nil, owner['key'])
    test_host1 = @cp.update_consumer({:uuid => test_host1.uuid, :guestIds => guest_set})

    host_facts = {
        "dmi.system.uuid" => host_hyp_id1,
        "virt.is_guest"=>"false",
        "fact1"=>"one",
        "fact2"=>"two",
        "fact3"=>"three"
    }
    job_detail = async_update_hypervisor(owner, user, host_hyp_id1, host_hyp_id1, guests, nil, nil, host_facts)
  end

  it 'should allow the hypervisor id to be changed on the consumer' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id_1 = "hypervisor_id_1"
    host_hyp_id_2 = "hypervisor_id_2"
    host_system_id = "system_id"
    guest_set = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests = ['g1', 'g2']

    test_host = user.register(host_hyp_id_1, :hypervisor, nil, {"dmi.system.uuid" => host_system_id, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_1)
    @cp.update_consumer({:uuid => test_host.uuid, :guestIds => guest_set})
    @cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_1

    async_update_hypervisor(owner, user, host_hyp_id_1, host_hyp_id_2, guests, true,nil, {"dmi.system.uuid" => host_system_id})
    test_host = @cp.get_consumer(test_host.uuid)
    @cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_2
  end

  it 'should allow the hypervisor id update on the consumer with no existing hypervisor id' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_name = "test_hypevisor_host_name"
    host_hyp_id = "test_hypervisor_id"
    host_system_id = "test_system_id"
    guest_set = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests = ['g1', 'g2']

    test_host = user.register(host_name, :hypervisor, nil, {"virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [])
    @cp.update_consumer({:uuid => test_host.uuid, :guestIds => guest_set, :facts => {"dmi.system.uuid" => host_system_id, "virt.is_guest"=>"false"}})
    expect(@cp.get_consumer(test_host.uuid)['hypervisorId']).to be_nil

    async_update_hypervisor(owner, user, host_name, host_hyp_id, guests, true,nil, {"dmi.system.uuid" => host_system_id})
    test_host = @cp.get_consumer(test_host.uuid)
    expect(@cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId']).to eq(host_hyp_id)
  end


  it 'check in will fail when json does not have the proper structure' do
    owner = create_owner random_string('test_owner1')
    user = user_client(owner, random_string("user"))
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    # incorrect json structure
    report = {
        "name" => '',
        "uuid" => uuid1,
        "hypervisorId" => {"hypervisorId" => 'hypervisor_id_1'},
        "guestIds" => [
            {'guestId' => uuid2}
        ],
        "facts" => {"test_fact" => "fact_value" }
    }
    virtwho = create_virtwho_client(@user)
    lambda do
      virtwho.hypervisor_update(@owner['key'], report.to_json())
    end.should raise_exception(RestClient::BadRequest)

    # empty json
    report = {}
    lambda do
      virtwho.hypervisor_update(@owner['key'], report.to_json())
    end.should raise_exception(RestClient::BadRequest)

    # this is the correct version of an empy list of hypervisors
    report = {hypervisors:[]}
    job_detail = send_host_guest_mapping(owner, user, report.to_json())
    job_detail["state"].should == "FINISHED"

  end

  it 'will allow the hardware id to change while the hypervisor id stays constant' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id = "hypervisor_id"
    host_system_id_1 = "system_id_1"
    host_system_id_2 = "system_id_2"
    reporter_id = "reporter"
    guest_set = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests = ['g1', 'g2']

    test_host = user.register(host_hyp_id, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_1, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id)
    @cp.update_consumer({:uuid => test_host.uuid, :guestIds => guest_set})
    @cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id

    async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests, true, reporter_id, {"dmi.system.uuid" => host_system_id_1})
    test_host = @cp.get_consumer(test_host.uuid)
    @cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id

    async_update_hypervisor(owner, user, host_hyp_id, host_hyp_id, guests, true, reporter_id, {"dmi.system.uuid" => host_system_id_2})
    test_host = @cp.get_consumer(test_host.uuid)
    @cp.get_consumer(test_host.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id
  end

  it 'can reconcile when hardware ids change across known hypervisors' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id_1 = random_string("09h06").downcase
    host_hyp_id_2 = random_string("09h07").downcase
    host_hyp_id_3 = random_string("09h10").downcase
    host_system_id_1 = random_string("bf").downcase
    host_system_id_2 = random_string("c0").downcase
    host_system_id_3 = random_string("c9").downcase
    host_system_id_4 = random_string("c6").downcase
    reporter_id = "reporter"
    guest_set_1 = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests_1 = ['g1', 'g2']
    guest_set_2 = [{"guestId"=>"g3"},{"guestId"=>"g4"}]
    guests_2 = ['g3', 'g4']
    guest_set_3 = [{"guestId"=>"g5"},{"guestId"=>"g6"}]
    guests_3 = ['g5', 'g6']

    test_host_1 = user.register(host_hyp_id_1, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_1, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_1)
    @cp.update_consumer({:uuid => test_host_1.uuid, :guestIds => guest_set_1})
    @cp.get_consumer(test_host_1.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_1

    test_host_2 = user.register(host_hyp_id_2, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_2, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_2)
    @cp.update_consumer({:uuid => test_host_2.uuid, :guestIds => guest_set_2})
    @cp.get_consumer(test_host_2.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_2

    test_host_3 = user.register(host_hyp_id_3, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_3, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_3)
    @cp.update_consumer({:uuid => test_host_3.uuid, :guestIds => guest_set_3})
    @cp.get_consumer(test_host_3.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_3

    async_update_hypervisor(owner, user, host_hyp_id_1, host_hyp_id_1, guests_1, true, reporter_id, {"dmi.system.uuid" => host_system_id_2})
    test_host_1 = @cp.get_consumer(test_host_1.uuid)
    test_host_1['hypervisorId']['hypervisorId'].should == host_hyp_id_1
    test_host_1['facts']['dmi.system.uuid'].should == host_system_id_2

    async_update_hypervisor(owner, user, host_hyp_id_2, host_hyp_id_2, guests_2, true, reporter_id, {"dmi.system.uuid" => host_system_id_4})
    test_host_2 = @cp.get_consumer(test_host_2.uuid)
    test_host_2['hypervisorId']['hypervisorId'].should == host_hyp_id_2
    test_host_2['facts']['dmi.system.uuid'].should == host_system_id_4

    async_update_hypervisor(owner, user, host_hyp_id_3, host_hyp_id_3, guests_3, true, reporter_id, {"dmi.system.uuid" => host_system_id_1})
    test_host_3 = @cp.get_consumer(test_host_3.uuid)
    test_host_3['hypervisorId']['hypervisorId'].should == host_hyp_id_3
    test_host_3['facts']['dmi.system.uuid'].should == host_system_id_1
  end

  it 'will update the consumer name from the hypervisor checkin' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id_1 = random_string("09h06").downcase
    host_system_id_1 = random_string("bf").downcase
    reporter_id = "reporter"
    new_name = random_string('new_name')
    guest_set_1 = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests_1 = ['g1', 'g2']

    test_host_1 = user.register(host_hyp_id_1, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_1, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_1)
    @cp.update_consumer({:uuid => test_host_1.uuid, :guestIds => guest_set_1})
    @cp.get_consumer(test_host_1.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_1

    async_update_hypervisor(owner, user, new_name, host_hyp_id_1, guests_1, true, reporter_id, {"dmi.system.uuid" => host_system_id_1})
    test_host_1 = @cp.get_consumer(test_host_1.uuid)
    test_host_1['name'].should == new_name
  end

  it 'will not update the nil consumer name from the hypervisor checkin' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    host_hyp_id_1 = random_string("09h06").downcase
    host_system_id_1 = random_string("bf").downcase
    reporter_id = "reporter"
    new_name = random_string('new_name')
    guest_set_1 = [{"guestId"=>"g1"},{"guestId"=>"g2"}]
    guests_1 = ['g1', 'g2']

    test_host_1 = user.register(host_hyp_id_1, :hypervisor, nil, {"dmi.system.uuid" => host_system_id_1, "virt.is_guest"=>"false"}, nil, owner['key'], [], [], nil, [], host_hyp_id_1)
    test_host_name = test_host_1['name']
    @cp.update_consumer({:uuid => test_host_1.uuid, :guestIds => guest_set_1})
    @cp.get_consumer(test_host_1.uuid)['hypervisorId']['hypervisorId'].should == host_hyp_id_1

    async_update_hypervisor(owner, user, nil, host_hyp_id_1, guests_1, true, reporter_id, {"dmi.system.uuid" => host_system_id_1})
    test_host_1 = @cp.get_consumer(test_host_1.uuid)
    test_host_1['name'].should == test_host_name
  end
end
