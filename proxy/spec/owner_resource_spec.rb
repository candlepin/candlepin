require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Owner Resource' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'should allow a client to create an owner with parent' do
    owner = create_owner random_string('test_owner')   
    child_owner = @cp.create_owner(random_string('test_owner'), owner)
    child_owner.parentOwner.id.should == owner.id
  end

  it 'should throw bad request exception when parentOwner is invalid' do
    fake_parent_owner = {
      'key' => 'something',
      'displayName' => 'something something',
      'id' => 'doesNotExist'
    }
    
    lambda do
      @cp.create_owner(random_string('child owner'), fake_parent_owner)
    end.should raise_exception(RestClient::BadRequest)
  end
end
