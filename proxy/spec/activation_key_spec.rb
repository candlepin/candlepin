require 'candlepin_scenarios'

# XXX: all these tests need work (but so do tokens)
describe 'Activation Keys' do

  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(name='some_product')

    @sub = @cp.create_subscription(@owner['key'], @some_product['id'], 37)
    @cp.refresh_pools(@owner.key)
    @pool = @cp.list_pools[0]
    activation_key = {
        'owner' => @owner,
        'name' => random_string('test_token'),
    }
    @activation_key = @cp.create_activation_key(activation_key)
  end

  it 'should allow owners to list existing activation keys' do
    keys = @cp.list_activation_keys()
    keys.length.should >= 1
  end

  it 'should allow owners to delete their activation keys' do
    @cp.delete_activation_key(@activation_key['id'])
  end

  it 'should allow pools to be added and removed to activation keys' do
    @cp.add_pool_to_key(@activation_key['id'], @pool['id'])
	key = @cp.get_activation_key(@activation_key['id'])
	puts key.to_json
	puts key['pools']
	key['pools'].length.should == 1
    @cp.remove_pool_from_key(@activation_key['id'], @pool['id'])
	key = @cp.get_activation_key(@activation_key['id'])
	key['pools'].length.should == 0
  end

end
