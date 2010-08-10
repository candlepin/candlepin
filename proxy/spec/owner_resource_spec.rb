require 'candlepin_scenarios'

describe 'Owner Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'should support lookup by key' do
    owner = create_owner random_string('test_owner')
    owners_list = @cp.get_owners()
    owners_list.should have_at_least(2).items
    owners_by_key_list = @cp.get_owners(:key => owner.key)
    owners_by_key_list.should have(1).items
  end

end
