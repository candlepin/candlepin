require 'candlepin_scenarios'

describe 'Candlepin Import' do

  include CandlepinMethods
  include ExportMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:all) do
    create_candlepin_export()
    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_owner_client = user_client(@import_owner, random_string('testuser'))
    import(@export_filename, @import_owner.id)
  end

  after(:all) do
    cleanup_candlepin_export()
    @cp.delete_owner(@import_owner.id)
  end

  def import(filename, owner_id)
    # Could probably do this with some lib call but sticking with Curl for now:
    system "curl -k -u admin:admin -F upload=@%s https://localhost:8443/candlepin/owners/%s/import" % [filename, owner_id]
  end

  it 'should succeed' do
    $?.should == 0
  end

  it 'should create pools' do
    pools = @import_owner_client.list_pools
    pools.length.should == 2
  end

  it 'should modify owner to reference upstream consumer' do
    o = @cp.get_owner(@import_owner.id)
    o.upstreamUuid.should == @candlepin_client.uuid
  end

end
