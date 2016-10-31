# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'

# To run this spec test it is necessary to have Qpid up and configured.
#
# In Jenkins environment, this should be a problem. The cp-test script 
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

  it 'should receive OWNER CREATED' do
     create_owner random_string("test")
     sleep 10
     msgs = @cq.receive
     msgs.length.should == 1
     msgs[0].subject.should == 'owner.created'
  end

end
