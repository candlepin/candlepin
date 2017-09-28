require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

describe 'Consumer Checkin Spec' do

  include CandlepinMethods


  before(:each) do
    @owner = create_owner(random_string('test_owner'))
    @username = random_string("user")
    @user = user_client(@owner, @username)
    @consumer_name = random_string("consumer")

    @consumer = @user.register(@consumer_name, :system, nil, {}, nil, nil)
    @ccp = Candlepin.new(nil, nil, @consumer['idCert']['cert'], @consumer['idCert']['key'])
  end

  it 'Updates last checkin time on consumer update' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.update_consumer(@consumer)
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  it 'Updates last checkin time on entitlement regeneration' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.regenerate_entitlement_certificates(consumer_uuid=@consumer['uuid'])
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  it 'Updates last checkin time when fetching entitlement certs' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.list_certificates(['123'], {:uuid => @consumer['uuid']})
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  it 'Updates last checkin time when fetching entitlement cert serials' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.list_certificate_serials(@consumer['uuid'])
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  it 'Updates last checkin time on hypervisor checkin' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.hypervisor_check_in(@owner['key'], {}, false)
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  it 'Updates last checkin time on async hypervisor checkin' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    def build_checkin_data(name, hypervisor_id, guest_id_list)
        guestIds = []

        guest_id_list.each do |guest|
            guestIds << {'guestId' => guest}
        end

        json = {"hypervisors" => [
            "name" => name,
            "hypervisorId" => {"hypervisorId" => hypervisor_id},
            "guestIds" => guestIds,
            "facts" => {"test_fact" => "fact_value"}
        ]}

        return json.to_json
    end

    hypervisor_data = build_checkin_data('test_hv', 'test_hv', ['guest-1', 'guest-2', 'guest-3'])

    @ccp.hypervisor_update(@owner['key'], hypervisor_data, false)
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to_not eq(last_checkin)
  end

  # TODO:
  # Add more checks for high-traffic or high-impact endpoints to verify we're not updating
  # the last checkin time in those.

  it 'Does not update last checkin when fetching releases' do
    # Delay a second or two so we don't accidentally updated to the identical timestamp on MySQL
    last_checkin = @consumer['lastCheckin']
    sleep 1

    @ccp.get_consumer_release(@consumer['uuid'])
    updated = @ccp.get_consumer(@consumer['uuid'])

    expect(updated['lastCheckin']).to eq(last_checkin)
  end
end
