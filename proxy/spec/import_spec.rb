require 'candlepin_scenarios'

describe 'Candlepin Import' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    @users = []
    create_candlepin_export()
    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_owner_client = user_client(@import_owner, random_string('testuser'))
    @cp.import(@import_owner.key, @export_filename)
  end

  after(:all) do
    cleanup_candlepin_export()
    @cp.delete_owner(@import_owner.key)
  end

  it 'creates pools' do
    pools = @import_owner_client.list_pools
    pools.length.should == 2
  end

  it 'modifies owner to reference upstream consumer' do
    o = @cp.get_owner(@import_owner.key)
    o.upstreamUuid.should == @candlepin_client.uuid
  end

  it 'should create a SUCCESS record of the import' do
    # Look for at least one valid entry
    @import_owner_client.list_imports(@import_owner.key).find_all do |import|
      import.status == 'SUCCESS'
    end.should_not be_empty
  end

  it 'should return a 409 on a duplicate import' do
    lambda do
      @cp.import(@import_owner.key, @export_filename)
    end.should raise_exception RestClient::Conflict
  end

  it 'should create a FAILURE record on a duplicate import' do
    # This is probably bad - relying on the previous test
    # to actually generate this record
    @import_owner_client.list_imports(@import_owner.key).find_all do |import|
      import.status == 'FAILURE'
    end.should_not be_empty
  end

  it 'should set the correct error status message' do
    # Again - relying on the 409 test to set this - BAD!
    error = @import_owner_client.list_imports(@import_owner.key).find do |import|
      import.status == 'FAILURE'
    end

    error.statusMessage.should == 'Import is older than existing data'
  end

end
