require 'spec_helper'
require 'candlepin_scenarios'

describe 'Hypervisor Resource', :type => :virt do
  include CandlepinMethods
  include VirtHelper

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?
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

    @host_uuid = results.updated[0]['uuid']
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
    should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_hyp_id, host_hyp_id)
  end

 it 'should add consumer to created when new host id and no guests reported - async' do
    host_hyp_id = random_string('host')
    host_name = random_string('name')
    job_detail = async_update_hypervisor(@owner, @consumer, host_name, host_hyp_id, [])
    job_detail['result'].should == 'Created: 1, Updated: 0, Unchanged:0, Failed: 0'
    result_data = job_detail['resultData']
    hyp_consumer = @cp.get_consumer(result_data.created[0]['uuid'])
    hyp_consumer.facts['test_fact'].should == 'fact_value'
    should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result_data, host_name, host_hyp_id)
  end

  def should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_name, host_hyp_id)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.created[0].name.should == host_name
    result.created[0].idCert.should be_nil
    # Test get_owner_hypervisors works, should return all
    hypervisors = @user.get_owner_hypervisors(@owner['key'])
    hypervisors.size.should == 2
    # Test lookup with hypervisor ids
    hypervisors = @user.get_owner_hypervisors(@owner['key'], [host_hyp_id])
    hypervisors.size.should == 1
    # Test lookup with nonexistant hypervisor id
    hypervisors = @user.get_owner_hypervisors(@owner['key'], ["probably not a hypervisor"])
    hypervisors.size.should == 0
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
    job_detail['result'].should == 'Created: 1, Updated: 0, Unchanged:0, Failed: 0'
    result_data = job_detail['resultData']
    should_add_consumer_to_created_when_new_host_id_and_guests_were_reported(result_data, host_name)
  end

  def should_add_consumer_to_created_when_new_host_id_and_guests_were_reported(result, host_name)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.created[0].name.should == host_name
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
    job_detail['result'].should == 'Created: 0, Updated: 0, Unchanged:0, Failed: 1'
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
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_updated_when_guest_ids_are_updated(result)
  end

  it 'should add consumer to updated when guest ids are updated - async' do
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id,  ['g1', 'g2'])
    job_detail['result'].should == 'Created: 0, Updated: 1, Unchanged:0, Failed: 0'
    result_data = job_detail['resultData']
    should_add_consumer_to_updated_when_guest_ids_are_updated(result_data)
  end

  def should_add_consumer_to_updated_when_guest_ids_are_updated(result)
    # Should only  have a result entry for updated.
    result.created.size.should == 0
    result.updated.size.should == 1
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.updated[0].name.should == @expected_host_name
  end

  it 'should add consumer to unchanged when same guest ids are sent' do
    mapping = get_host_guest_mapping(@expected_host_hyp_id, @expected_guest_ids)
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result)
  end

  it 'should add consumer to unchanged when same guest ids are sent - async' do
    job_detail = async_update_hypervisor(@owner, @consumer, @expected_host_name, @expected_host_hyp_id, @expected_guest_ids)
    job_detail['result'].should == 'Created: 0, Updated: 0, Unchanged:1, Failed: 0'
    result_data = job_detail['resultData']
    should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result_data)
  end

  def should_add_consumer_to_unchanged_when_same_guest_ids_are_sent(result)
    # Should only  have a result entry for unchanged.
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0
    # verify our created consumer is correct.
    result.unchanged[0].name.should == @expected_host_name
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
    job_detail['result'].should == 'Created: 1, Updated: 0, Unchanged:0, Failed: 0'
    result_data = job_detail['resultData']
    result_data.created.size.should == 1
    result_data.created[0].name.should == host_name

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
    # verify our unchanged consumer is correct.
    result.unchanged[0].name.should == host_name
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

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
    @host_client.list_entitlements.length.should == 1
  end

  it 'should initialize guest ids to empty when creating new host' do
    host_guest_mapping = get_host_guest_mapping(random_string('host'), [])
    results = @consumer.hypervisor_check_in(@owner['key'], host_guest_mapping)
    # Host consumer should have been created.
    results.created.size.should == 1
    results.created[0]['guestIds'].should_not == nil
  end

  it 'should initialize guest ids to empty when creating new host - async' do
    job_detail = async_update_hypervisor(@owner, @consumer, random_string('name'), random_string('host'), [])
    result_data = job_detail['resultData']
    # Host consumer should have been created.
    result_data.created.size.should == 1
    result_data.created[0]['guestIds'].should_not == nil
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

    job_detail = async_update_hypervisor(@owner, virtwho1, host_name, host_hyp_id, ['g1', 'g2'])
    job_detail['resultData'].unchanged.size.should == 1
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

  it 'should see the capability that corresponds to the async method' do
    json = @cp.get_status()
    json['managerCapabilities'].should include("hypervisors_async")
  end

  def get_host_guest_mapping(host_hyp_id, guest_id_list)
    return { host_hyp_id => guest_id_list }
  end

  def get_async_host_guest_mapping(name, hypervisor_id, guest_id_list)
      guestIds = []
      guest_id_list.each do |guest|
          guestIds << {'guestId' => guest}
      end
      json = {"hypervisors" => ["name" => name,
                                "hypervisorId" =>
                                    {"hypervisorId" => hypervisor_id},
                                "guestIds" => guestIds,
                                "facts" => {"test_fact" => "fact_value"}]}
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
    # Ensure that we are returning the updated consumer correctly.
    check_hypervisor_consumer(result.updated[0], @expected_host_name, updated_guest_ids)
    # Check that all updates were persisted correctly.
    consumer = @cp.get_consumer(@host_uuid)
    check_hypervisor_consumer(consumer, @expected_host_name, updated_guest_ids, reporter_id)
  end

  def check_hypervisor_consumer(consumer, expected_host_name, expected_guest_ids, reporter_id=nil)
    consumer['name'].should == expected_host_name

    guest_ids = consumer['guestIds']
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

  def async_update_hypervisor(owner, consumer, host_name, host_hyp_id, guests, create=true, reporter_id=nil)
    host_mapping = get_async_host_guest_mapping(host_name, host_hyp_id, guests)
    job_detail = JSON.parse(consumer.hypervisor_update(owner['key'], host_mapping, create, reporter_id))
    wait_for_job(job_detail['id'], 5)
    job_detail = @cp.get_job(job_detail['id'], true)
    job_detail['state'].should == 'FINISHED'
    job_detail['result'].should_not be_nil
    return job_detail
  end

  def create_virtwho_client(user)
    consumer = user.register(random_string("virt-who"), :system, nil, {},
        nil, nil, [], [{:productId => 'installedprod',
           :productName => "Installed"}])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    return consumer_client
  end
end
