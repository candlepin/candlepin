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

  before(:each) do
     @cq = CandlepinQpid.new
     skip("Qpid keys not found, skipping Qpid spec tests") if @cq.no_keys 
     # Clean the qpid queue
     @cq.receive
  end

  it 'flow stop is detected' do
     assert_mode("NORMAL")
     # Create a queue that will initiate flow control after
     # 3 messages
     test_q = "flowStopQueue"
     @cq.create_queue(test_q, '--flow-stop-count=3', 'event') 
     puts "#{test_q} queue created"
     2.times do 
       create_owner random_string("test")
     end
     puts "2 owners should be successfully created now"
     assert_mode("NORMAL")
     puts "Creating additional owners which will FLOW_STOP #{test_q}"
     3.times do 
       create_owner random_string("test")
     end
     sleep 10
     assert_mode_reason("SUSPEND", "QPID_FLOW_STOPPED")

     puts "Removing queue #{test_q}, this should unblock again"
     @cq.delete_queue(test_q) 
     sleep 20
     assert_mode_reason("NORMAL", "QPID_UP")
  end 
  
  def assert_mode_reason(mode_name, reason)
     mode = @cp.get('/status/mode')
     expect(mode).to have_key("mode")
     expect(mode["mode"]).to eq(mode_name)
     expect(mode["reason"]).to eq(reason)
  end

  def assert_mode(mode_name)
     mode = @cp.get('/status/mode')
     expect(mode).to have_key("mode")
     expect(mode["mode"]).to eq(mode_name)
  end


  it 'transition to SUSPEND mode' do
     assert_mode("NORMAL")

     puts "Candlepin is in Normal mode. Stopping Qpid"
     @cq.stop
     sleep 40
     
     assert_mode_reason("SUSPEND", "QPID_DOWN")

     puts "Candlepin is in Suspend mode. Creating owner"

     begin
       create_owner random_string('test_owner')
     rescue RestClient::ServiceUnavailable => un
       displayMessage = JSON.parse(un.response.body)["displayMessage"]
       displayMessage.should == 'Candlepin is in Suspend mode, please check /status/mode resource to get more details'
     end 

     puts "Starting Qpid"
     @cq.start
     sleep 40

     assert_mode_reason("NORMAL", "QPID_UP")
     
     puts "Candlepin in Normal mode. Creating owner"
     expect(create_owner random_string('test_owner')).to have_key("created")
     sleep 15
     msgs = @cq.receive
     msgs.length.should == 1
     msgs[0].subject.should == 'owner.created'
  end


  it 'reconnect should work' do
     @cq.stop
     puts "Qpid stopped, creating owner"
     create_owner random_string("test")
     sleep 10
     
     @cq.start 
     puts "Qpid restarted, querying queues"
     sleep 1
     msgs = @cq.receive
     msgs.length.should == 0
    
     puts "Nothing found in the queues. Waiting for reconnect"
     
     sleep 15 
     create_owner random_string("test")
     sleep 3
     msgs = @cq.receive
     msgs.length.should == 1
     msgs[0].subject.should == 'owner.created'
  end


end
