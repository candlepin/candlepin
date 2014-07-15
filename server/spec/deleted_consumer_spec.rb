require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

describe 'Deleted Consumer Resource' do

  include CandlepinMethods

  it 'find all deleted consumers' do
    date = Time.now.utc.iso8601
    # make sure we have at least one deleted record
    owner1 = create_owner random_string('test_owner1')
    username1 = random_string("user1")
    consumername1 = random_string("consumer1")
    user1 = user_client(owner1, username1)
    consumer1 = consumer_client(user1, consumername1)
    uuid = consumer1.uuid
    consumer1.unregister(consumer1.uuid)

    deleted_consumers = @cp.get_deleted_consumers(date=date)
    deleted_consumers.map { |i| i['consumerUuid'] }.should include(uuid)
  end

end
