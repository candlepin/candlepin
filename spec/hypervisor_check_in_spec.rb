require 'spec_helper'
require 'candlepin_scenarios'
require 'thread'

describe 'Hypervisor Resource', :type => :virt do
  include CandlepinMethods
  include VirtHelper
  include AttributeHelper
  include CertificateMethods

  before(:each) do
    skip("candlepin running in standalone mode") if is_hosted?
    @expected_host_hyp_id = random_string("host")
    @expected_host_name = random_string("name")
    @expected_guest_ids = [@uuid1, @uuid2]

    # we must register the consumer to use it as a client
    # hypervisor check in creation does not result in a client cert
    consumer = @user.register(@expected_host_name, :hypervisor, nil, {"test_fact" => "fact_value"},
        nil, nil, [], [], [], [], @expected_host_hyp_id)

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

  it 'should add consumer to created when new host id and no guests reported' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, [])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping)
    should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_hyp_id,
      host_hyp_id)
  end

  def should_add_consumer_to_created_when_new_host_id_and_no_guests_reported(result, host_hyp_name,
    host_hyp_id)
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0

    result.created[0].name.should == host_hyp_name
    result.created[0].owner['key'].should == @owner['key']

    # verify our created consumer is correct.
    created_consumer = @cp.get_consumer(result.created[0].uuid)
    created_consumer.name.should == host_hyp_name
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
    # Should only  have a result entry for created.
    result.created.size.should == 1
    result.updated.size.should == 0
    result.unchanged.size.should == 0
    result.failedUpdate.size.should == 0

    result.created[0].name.should == host_hyp_id
    result.created[0].owner['key'].should == @owner['key']

    created_consumer =  @cp.get_consumer(result.created[0].uuid)
    # verify our created consumer is correct.
    created_consumer.name.should == host_hyp_id
    # verify last checkin time is updated
    created_consumer.lastCheckin.should_not be_nil
  end

  it 'should not add new consumer when create_missing is false' do
    host_hyp_id = random_string('host')
    mapping = get_host_guest_mapping(host_hyp_id, ['g1'])
    result = @consumer.hypervisor_check_in(@owner['key'], mapping, false)
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
    result.created.size.should == 0
    result.updated.size.should == 0
    result.unchanged.size.should == 1
    result.failedUpdate.size.should == 0

    result.unchanged[0].name.should == host_hyp_id
    result.unchanged[0].owner['key'].should == @owner['key']

    unchanged_consumer = @cp.get_consumer(result.unchanged[0].uuid)
    # verify our unchanged consumer is correct.
    unchanged_consumer.name.should == host_hyp_id
  end

  it 'should update host guest ids as consumer' do
    update_guest_ids_test(@consumer)
  end

  it 'should update host guest ids as user' do
    update_guest_ids_test(@user)
  end

  it 'should not revoke guest entitlements when guest no longer registered' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)

    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, [@uuid2])
    results = @consumer.hypervisor_check_in(@owner['key'],  updated_host_guest_mapping)
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
    results.created.size.should == 0
    results.updated.size.should == 1

    results.updated[0].name.should == @expected_host_name
    results.updated[0].owner['key'].should == @owner['key']

    @host_client.list_entitlements.length.should == 1
  end

  it 'should not revoke host and guest entitlements when guestId list is empty' do
    guest_pool = find_guest_virt_pool(@guest1_client, @guest1.uuid)
    @guest1_client.consume_pool(guest_pool.id, {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host stops reporting guest:
    updated_host_guest_mapping = get_host_guest_mapping(@expected_host_hyp_id, [])
    results = @consumer.hypervisor_check_in(@owner['key'], updated_host_guest_mapping)
    results.created.size.should == 0
    results.updated.size.should == 1

    results.updated[0].name.should == @expected_host_name
    results.updated[0].owner['key'].should == @owner['key']

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

  it 'should block virt-who if owner does not match identity cert' do
    virtwho1 = create_virtwho_client(@user)
    host_guest_mapping = get_host_guest_mapping(random_string('my-host'), ['g1', 'g2'])
    lambda do
      results = virtwho1.hypervisor_check_in(@owner2['key'], host_guest_mapping)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should raise bad request exception if mapping was not provided' do
    virtwho = create_virtwho_client(@user)
    lambda do
      virtwho.hypervisor_check_in(@owner['key'], nil)
    end.should raise_exception(RestClient::BadRequest)
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
    hypervisor_id_1 = random_string('hypervisor').downcase
    hypervisor_id_2 = random_string('hypervisor').downcase
    virtwho = create_virtwho_client(user)
    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]

    host_consumer = user.register(random_string('host1'), :hypervisor, nil,
      {}, nil, owner['key'], [], [], [], [], hypervisor_id_1)
    new_host_consumer = user.register(random_string('host2'), :hypervisor, nil,
      {}, nil, owner['key'], [], [], [], [], hypervisor_id_2)

    guest_consumer = user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, owner['key'], [], [])
    # Create a product/subscription
    super_awesome = create_product(nil, random_string('super_awesome'),
                            :attributes => { "virt_limit" => "10", "host_limited" => "true" }, :owner => owner['key'])
    create_pool_and_subscription(owner['key'], super_awesome.id, 20)
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    new_consumer_client = Candlepin.new(nil, nil, new_host_consumer['idCert']['cert'], new_host_consumer['idCert']['key'])
    guest_client = Candlepin.new(nil, nil, guest_consumer['idCert']['cert'], guest_consumer['idCert']['key'])

    virtwho.hypervisor_check_in(owner['key'], get_host_guest_mapping(hypervisor_id_1, [uuid1]))

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

    #because mysql
    sleep 2
    virtwho.hypervisor_check_in(owner['key'], get_host_guest_mapping(hypervisor_id_2, [uuid1]))

    # The guests host limited entitlement should be gone
    guest_client.list_entitlements().length.should == 0
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

  it 'guest should keep conditional content after migration' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    rh00271_eng_product = create_product("204", "Red Hat Enterprise Linux Server - Extended Life Cycle Support", {:owner => owner['key']})
    rh00271_product = create_product("RH00271", "Extended Life Cycle Support (Unlimited Guests)", {
      :owner => owner['key'],
      :providedProducts => [rh00271_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "RH00271",
        :host_limited => "true"
      }
    })

    rh00051_eng_product = create_product("69", "Red Hat Enterprise Linux Server", {:owner => owner['key']})
    rh00051_product = create_product("RH00051", "Red Hat Enterprise Linux for Virtual Datacenters with Smart Management, Standard", {
      :owner => owner['key'],
      :providedProducts => [rh00051_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "RH00051",
        :host_limited => "true"
      }
    })

    rh00051_content = @cp.create_content(
      owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
      {:content_url=> '/this/is/the/path'}, true)

    # Content that has a required/modified product 'rh00051_eng_product' (this eng product needs to be entitled to the
    # consumer already, or otherwise this content will get filtered out during entitlement cert generation)
    rh00271_content = @cp.create_content(
      owner['key'], "cname-c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
      {:content_url=> '/this/is/the/path', :modified_products => [rh00051_eng_product["id"]]}, true)

    @cp.add_content_to_product(owner['key'], rh00051_eng_product['id'], rh00051_content['id'], true)
    @cp.add_content_to_product(owner['key'], rh00271_eng_product['id'], rh00271_content['id'], true)

    # creating master pool for the RH00051 product
    rh00051_pool = @cp.create_pool(owner['key'], rh00051_product['id'], {:quantity => 10,
     :subscription_id => "random",
     :upstream_pool_id => "random",
     :provided_products => [rh00051_eng_product.id],
     :locked => "true"})

    #creating master pool for the RH00271 product
    rh00271_pool = @cp.create_pool(owner['key'], rh00271_product['id'], {:quantity => 10,
     :subscription_id => "random",
     :upstream_pool_id => "random",
     :provided_products => [rh00271_eng_product.id],
     :locked => "true"})
    #creating hypervisor 1
    host_hyp_id = random_string("hypervisor-1")
    host_name1 = random_string("hypervisor-name-1")
    hypervisor_1 = user.register(host_hyp_id, :hypervisor, nil, {}, nil, owner['key'], [], [], nil, [], host_name1)
    hypervisor_1_client = Candlepin.new(nil, nil, hypervisor_1['idCert']['cert'], hypervisor_1['idCert']['key'])

    #creating guest
    guest = random_string('guest')
    installed_prods = [{'productId' => rh00271_eng_product['id'], 'productName' => rh00271_eng_product['name']},
     {'productId' => rh00051_eng_product['id'], 'productName' => rh00051_eng_product['name']}]
    guest1 = user.register(guest, :system, nil, {"virt.is_guest"=>"true",'virt.uuid' => guest,
      "system.certificate_version" => "3.2" }, nil, owner['key'], [], installed_prods)
    guest1_client = Candlepin.new(nil, nil, guest1['idCert']['cert'], guest1['idCert']['key'])

    # guest mapping (hypervisor1 => guest) is done by virtwho
    virtwho = create_virtwho_client(user)
    virtwho.hypervisor_check_in(owner['key'], get_host_guest_mapping(host_name1, [guest]))

    # creating hypervisor 2
    host_hyp_id = random_string("hypervisor-2")
    host_name2 = random_string("hypervisor-name-2")
    hypervisor_2 = user.register(host_hyp_id, :hypervisor, nil, {}, nil, owner['key'], [], [], nil, [], host_name2)
    hypervisor_2_client = Candlepin.new(nil, nil, hypervisor_2['idCert']['cert'], hypervisor_2['idCert']['key'])

    # Hypervisor1 consumes the same pool
    hypervisor_1_client.consume_pool(rh00271_pool.id,{ :quantity => 1})  # stacked derived pool1 created
    stack_der_pool = find_derived_pool_for_host(owner['key'], "STACK_DERIVED", rh00271_product.id, hypervisor_1.uuid)
    hypervisor_1_stacked_derived_pool1 = stack_der_pool.id
    hypervisor_1_client.consume_pool(rh00051_pool.id,{ :quantity => 1})  # stacked derived pool2 created
    stack_der_pool = find_derived_pool_for_host(owner['key'], "STACK_DERIVED", rh00051_product.id, hypervisor_1.uuid)
    hypervisor_1_stacked_derived_pool2 = stack_der_pool.id

    # Hypervisor2 consumes the same pool
    hypervisor_2_client.consume_pool(rh00271_pool.id,{ :quantity => 1})  # stacked derived pool1 created
    stack_der_pool = find_derived_pool_for_host(owner['key'], "STACK_DERIVED", rh00271_product.id, hypervisor_2.uuid)
    hypervisor_2_stacked_derived_pool1 = stack_der_pool.id
    hypervisor_2_client.consume_pool(rh00051_pool.id,{ :quantity => 1})  # stacked derived pool2 created
    stack_der_pool = find_derived_pool_for_host(owner['key'], "STACK_DERIVED", rh00051_product.id, hypervisor_2.uuid)
    hypervisor_2_stacked_derived_pool2 = stack_der_pool.id

    # guest consumes hypervisor1's derived pools
    guest_rh00051_entitlement = guest1_client.consume_pool(hypervisor_1_stacked_derived_pool2, { :quantity => 1})[0]
    expect(guest_rh00051_entitlement.pool.id).to eq(hypervisor_1_stacked_derived_pool2)
    guest_rh00271_entitlement = guest1_client.consume_pool(hypervisor_1_stacked_derived_pool1, { :quantity => 1})[0]
    expect(guest_rh00271_entitlement.pool.id).to eq(hypervisor_1_stacked_derived_pool1)


    # Verify guest's entitlement certs each contain the appropriate content set
    certs = guest1_client.list_certificates
    expect(certs.length).to eq(2)

    rh00051_cert = nil
    rh00271_cert = nil
    certs.each { |cert|
        if cert['serial']['serial'] == guest_rh00051_entitlement['certificates'][0]['serial']['serial']
          rh00051_cert = cert['cert']
        end

        if cert['serial']['serial'] == guest_rh00271_entitlement['certificates'][0]['serial']['serial']
          rh00271_cert = cert['cert']
        end
    }
    expect(rh00051_cert).to_not be_nil
    expect(rh00271_cert).to_not be_nil

    json_body = extract_payload(rh00051_cert)
    expect(json_body['products'][0]['content'].size).to eq(1)
    expect(json_body['products'][0]['content'][0].id).to eq(rh00051_content.id)
    json_body = extract_payload(rh00271_cert)
    expect(json_body['products'][0]['content'].size).to eq(1)
    expect(json_body['products'][0]['content'][0].id).to eq(rh00271_content.id)


    # Migrate the guest from hypervisor1 to hypervisor2
    after_migration = {
      "hypervisors" => [
        {
          "name" => host_name1,
          "hypervisorId" => {"hypervisorId" => host_name1},
          "guestIds" => []
        },
        {
          "name" => host_name2,
          "hypervisorId" => {"hypervisorId" => host_name2},
          "guestIds" => [{'guestId' => guest}]
        }
      ]
    }
    job_detail = send_host_guest_mapping(owner, virtwho, after_migration.to_json())
    expect(job_detail["state"]).to eq("FINISHED")


    # At this point, the guest will have the entitlements from the old hypervisor revoked, and it will get the
    # entitlements from the new hypervisor auto-attached
    certs = guest1_client.list_certificates
    expect(certs.length).to eq(2)

    # Verify guest's entitlement certs each contain the appropriate content set
    ents = guest1_client.list_entitlements({:regen => false})
    expect(ents.length).to eq(2)

    updated_guest_rh00271_entitlement = ents.select { |ent| ent.pool.productId == guest_rh00271_entitlement.pool.productId }[0]
    expect(updated_guest_rh00271_entitlement).to_not be_nil

    updated_guest_rh00051_entitlement = ents.select { |ent| ent.pool.productId == guest_rh00051_entitlement.pool.productId }[0]
    expect(updated_guest_rh00051_entitlement).to_not be_nil

    rh00051_cert = nil
    rh00271_cert = nil
    certs.each { |cert|
        if cert['serial']['serial'] == updated_guest_rh00051_entitlement['certificates'][0]['serial']['serial']
          rh00051_cert = cert['cert']
        end

        if cert['serial']['serial'] == updated_guest_rh00271_entitlement['certificates'][0]['serial']['serial']
          rh00271_cert = cert['cert']
        end
    }
    expect(rh00051_cert).to_not be_nil
    expect(rh00271_cert).to_not be_nil

    json_body = extract_payload(rh00051_cert)
    expect(json_body['products'][0]['content'].size).to eq(1)
    expect(json_body['products'][0]['content'][0].id).to eq(rh00051_content.id)

    # rh00271_content (which depends on modified product id 69) should not have been filtered out, because the
    # engineering product 69 should already be covered by entitlement rh00051:
    json_body = extract_payload(rh00271_cert)
    expect(json_body['products'][0]['content'].size).to eq(1)
    expect(json_body['products'][0]['content'][0].id).to eq(rh00271_content.id)
  end

  def find_derived_pool_for_host(owner_key=nil, type=nil, product_id=nil, hypervisor_uuid=nil)
    pools = @cp.list_owner_pools(owner_key)
    selected = pools.select { |pool| pool['type'] == type && pool['productId'] == product_id }
    selected.each { |pool|
    req_host_attr = pool['attributes'].select{ |attr| attr['name'] == 'requires_host' }[0]
      if req_host_attr['value'] == hypervisor_uuid
        return pool
      end
    }
  end
end
