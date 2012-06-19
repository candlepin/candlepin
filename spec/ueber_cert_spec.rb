require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Uebercert' do
  include CandlepinMethods
  include CandlepinScenarios

  it 'owner can be deleted' do
    owner = @cp.create_owner random_string("test_owner1")
    @cp.generate_ueber_cert(owner['key'])
    @cp.delete_owner(owner['key'], false)

    lambda do
      @cp.get_owner(owner['key'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

end

