require 'spec_helper'
require 'candlepin_scenarios'
require 'thread'

describe 'Hypervisor Resource - Heartbeat Endpoint', :type => :virt do
  include CandlepinMethods
  include VirtHelper

  before(:each) do
    skip("candlepin running in standalone mode") if is_hosted?

    @expected_host_hyp_id = random_string("host")
    @expected_reporter_id = random_string("reporter")
    @expected_host_name = random_string("name")
    @expected_guest_ids = [@uuid1, @uuid2]

    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))

    # we must register the consumer to use it as a client
    # hypervisor check in creation does not result in a client cert
    @consumer = @user.register(@expected_host_name, :hypervisor, nil, {"test_fact" => "fact_value"},
      nil, @owner['key'], [], [], nil, [], @expected_host_hyp_id,
      [], nil, '2010-01-18T19:25:41+0000', nil, nil, nil, 0, nil, nil, nil, nil, [], @expected_reporter_id)

    @client = consumer_client(@user, random_string("consumer"))
  end

  it 'should update last checkin date of consumers of given reporter' do
    job = @client.hypervisor_heartbeat_update(@owner['key'], @expected_reporter_id)

    expect(job).to_not be_nil
    expect(job['name']).to include("Hypervisor Heartbeat Update")

    wait_for_job(job['id'], 10)

    consumer = @cp.get_consumer(@consumer.uuid)

    expect(Date.parse(consumer['lastCheckin'])).to eq(Date.today)
  end

end
