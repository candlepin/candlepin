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
  
  it 'detects Qpid DOWN' do
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'CONNECTED'
     @cq.stop
     puts "Turned off QPID, status should be DOWN now"
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'DOWN'
     @cq.start  
     puts "Started Qpid again, status should be CONNECTED now"
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'CONNECTED'
  end

  it 'flow stop is detected' do
     @cp.get("/status/recon")
     # Create a queue that will initiate flow control after
     # 3 messages
     test_q = "flowStopQueue"
     @cq.create_queue(test_q, '--flow-stop-count=3', 'event') 
     puts "#{test_q} queue created"
     2.times do 
       create_owner random_string("test")
     end
     puts "2 owners should be successfully created now"
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'CONNECTED'

     puts "Creating additional owners which will FLOW_STOP #{test_q}"
     3.times do 
       create_owner random_string("test")
     end
     sleep 5 
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'FLOW_STOPPED'

     puts "Removing queue #{test_q}, this should unblock again"
     @cq.delete_queue(test_q) 
     status = @cp.get("/status/brokerStatus") 
     status['status'].should == 'CONNECTED'
  end 

  it 'should receive OWNER CREATED' do
     @cp.get("/status/recon")
     create_owner random_string("test")
     sleep 10
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
    
     puts "Nothing found in the queues. Reconnecting"
     
     @cp.get("/status/recon")
     sleep 3
     create_owner random_string("test")
     sleep 3
     msgs = @cq.receive
     msgs.length.should == 1
     msgs[0].subject.should == 'owner.created'
  end


end
