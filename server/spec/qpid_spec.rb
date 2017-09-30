# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'

# To run this spec test it is necessary to have Qpid up and configured.
#
# In Jenkins environment, this shouldn't be a problem. The cp-test script
# has -q switch that will run configure-qpid.sh during candlepin server
# deploy.
#
# Outside of Jenkins environment, you must install Qpid and run the
# configure-qpid.sh yourself. Also you must enable AMQP (Qpid) in your
# candlepin config file. This can be done either manually or just by
# using deploy.sh script with -q switch.
describe 'Qpid Broker' do

  include CandlepinMethods


  def assert_mode(mode, status = nil)
    if status == nil
      status = @cp.get('/status')
    end

    expect(status).to have_key("mode")
    expect(status["mode"]).to eq(mode)
  end

  def assert_mode_and_reason(mode, reason, status = nil)
    if status == nil
      status = @cp.get('/status')
    end

    expect(status).to have_key("mode")
    expect(status).to have_key("modeReason")
    expect(status["mode"]).to eq(mode)
    expect(status["modeReason"]).to eq(reason)
  end

  def wait_for_mode(mode, max_wait_time = 90, poll_delay = 3)
    ellapsed = 0
    last_status = nil

    loop do
      begin
        last_status = @cp.get('/status')
        expect(last_status).to have_key("mode")

        if last_status['mode'] == mode
          break
        end
      rescue
        # Likely connection refused or timed out waiting on candlepin. Just ignore it and we'll try
        # again in a few seconds
        last_status = nil
      end

      break if ellapsed >= max_wait_time

      sleep poll_delay
      ellapsed += poll_delay
    end

    return last_status
  end

  def wait_for_event(subject, max_wait_time = 90)
    start_time = Time.now

    loop do
      begin
        timeout = max_wait_time - (Time.now - start_time)
        break if timeout <= 0

        msgs = @cq.receive(1, true, timeout)

        msgs.each do |msg|
          if msg.subject == subject
            return msg
          end
        end
      rescue Qpid::Proton::TimeoutError
        # Return nil since we didn't receive the message in time.
        break
      end
    end

    return nil
  end

  before(:each) do
    @cq = CandlepinQpid.new('amqps://localhost:5671/allmsg', 'server/bin/qpid/keys/qpid_ca.crt', 'server/bin/qpid/keys/qpid_ca.key')
    skip("Qpid keys not found, skipping Qpid spec tests") if @cq.no_keys?

    # Create an owner to guarantee we have an event to consume.
    expect(create_owner(random_string('test_owner'))).to have_key("created")

    # Consume any existing messages that may be in the queue already, blocking until we receive
    # at least one.
    msgs = @cq.receive
    expect(msgs.length).to be > 0
  end

  it 'should detect FLOW_STOP' do
    assert_mode("NORMAL")

    # Create a queue that will initiate flow control after 3 messages
    queue = "flowStopQueue"
    @cq.create_queue(queue, 'event', '--flow-stop-count=3')
    # puts "Queue created: #{queue}"

    # Create two users to generate two messages
    owners = 0

    # puts "Creating first block of owners..."
    2.times do
      owners += 1
      create_owner(random_string("test-owner_#{owners}"))
    end

    # We should still be in a good state here
    assert_mode("NORMAL")

    # Create additional owners, which should fill our queue and cause a FLOW_STOP
    # puts "Creating second block of owners..."
    3.times do
      owners += 1
      create_owner(random_string("test-owner_#{owners}"))
    end

    # We should eventually be suspended with a FLOW_STOPPED error
    status = wait_for_mode("SUSPEND")
    assert_mode_and_reason("SUSPEND", "QPID_FLOW_STOPPED", status)

    # Removing the queue should restore normal operations...
    # puts "Removing queue: #{queue}"
    @cq.delete_queue(queue)

    status = wait_for_mode("NORMAL")
    assert_mode_and_reason("NORMAL", "QPID_UP", status)
  end

  it 'should transition to SUSPEND mode' do
    assert_mode("NORMAL")

    # puts "Candlepin is in Normal mode. Stopping Qpid"
    @cq.stop

    status = wait_for_mode("SUSPEND")
    assert_mode_and_reason("SUSPEND", "QPID_DOWN", status)

    # puts "Candlepin is in Suspend mode. Creating owner"

    begin
      create_owner random_string('test_owner')
      fail("Expected error did not occur")
    rescue RestClient::ServiceUnavailable => un
      displayMessage = JSON.parse(un.response.body)["displayMessage"]
      displayMessage.should == 'Candlepin is in Suspend mode, please check /status resource to get more details'
    end

    # puts "Starting Qpid"
    @cq.start

    status = wait_for_mode("NORMAL")
    assert_mode_and_reason("NORMAL", "QPID_UP", status)

    # puts "Candlepin in Normal mode. Creating owner"
    owner = create_owner(random_string('test_owner'))
    expect(owner).to have_key("created")

    start_time = Time.now
    loop do
      event = wait_for_event('owner.created', 90 - (Time.now - start_time))
      expect(event).to_not be_nil
      expect(event.body).to_not be_nil

      body = JSON.parse(event.body)
      break if body['targetName'] == owner['key']
    end
  end

  it 'should transition to NORMAL mode' do
    assert_mode("NORMAL")

    # puts "Stopping Qpid and creating owner..."
    @cq.stop
    expect(create_owner(random_string('test_owner'))).to have_key("created")

    status = wait_for_mode("SUSPEND")
    assert_mode_and_reason("SUSPEND", "QPID_DOWN", status)

    # puts "Restarting Qpid and querying queues..."
    @cq.start

    # puts "Waiting for Candlepin to reconnect to Qpid..."
    status = wait_for_mode("NORMAL")
    assert_mode("NORMAL", status)

    # puts "Reconnected. Creating owner and waiting for the message..."
    owner = create_owner(random_string('test_owner'))
    expect(owner).to have_key("created")

    start_time = Time.now
    loop do
      event = wait_for_event('owner.created', 90 - (Time.now - start_time))
      expect(event).to_not be_nil
      expect(event.body).to_not be_nil

      body = JSON.parse(event.body)
      break if body['targetName'] == owner['key']
    end
  end

  it 'should start in SUSPEND mode without Qpid' do
    def start_tomcat()
      `if which supervisorctl > /dev/null 2>&1; then sudo supervisorctl start tomcat; else sudo systemctl start tomcat; fi`
    end

    def stop_tomcat()
      `if which supervisorctl > /dev/null 2>&1; then sudo supervisorctl stop tomcat; else sudo systemctl stop tomcat; fi`
    end

    assert_mode("NORMAL")
    stop_tomcat

    @cq.stop
    start_tomcat

    status = wait_for_mode("SUSPEND")
    assert_mode("SUSPEND", status)

    @cq.start

    status = wait_for_mode("NORMAL")
    assert_mode("NORMAL", status)
  end

end
