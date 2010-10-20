require 'candlepin_scenarios'

describe 'Autobind' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'selects the pool with the shortest expiration date' do
  end

  it 'selects the fewest pools possible' do
  end

end
