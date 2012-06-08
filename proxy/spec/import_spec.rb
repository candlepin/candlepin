require 'candlepin_scenarios'
require 'json'

describe 'Candlepin Import' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    @users = []
    create_candlepin_export()
    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_owner_client = user_client(@import_owner, random_string('testuser'))
    @cp.import(@import_owner['key'], @export_filename)
  end

  after(:all) do
    cleanup_candlepin_export()
    @cp.delete_owner(@import_owner['key'])
  end

  it 'creates pools' do
    pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
    pools.length.should == 2
  end

  it 'ignores multiplier for pool quantity' do
    pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
    pools.length.should == 2

    # 1 product has a multiplier of 2 upstream, the other 1.
    # 1 entitlement is consumed from each pool for the export, so
    # quantity should be 1 on both.
    pools[0]['quantity'].should == 1
    pools[1]['quantity'].should == 1
  end

  it 'modifies owner to reference upstream consumer' do
    o = @cp.get_owner(@import_owner['key'])
    o.upstreamUuid.should == @candlepin_client.uuid
  end

  it "originating information should be populted in the import" do
    @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
      consumer = @candlepin_client.get_consumer()
      import['generatedBy'].should == consumer['name']
      import['generatedDate'].should_not be_nil
      import['upstreamName'].should == consumer['name']
      import['upstreamId'].should == consumer['uuid']
      import['upstreamType'].should == consumer['type']['label'] 
      import.include?('webAppPrefix').should be_true
    end
  end

  it 'can be undone' do
    # Make a custom subscription so we can be sure it does not get wiped
    # out during either the undo or a subsequent re-import:
    custom_product = @cp.create_product(random_string(), random_string())
    custom_sub = @cp.create_subscription(@import_owner['key'], custom_product['id'])

    job = @import_owner_client.undo_import(@import_owner['key'])
    wait_for_job(job['id'], 30)
    pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
    pools.length.should == 1 # this is our custom pool
    pools[0]['subscriptionId'].should == custom_sub['id']
    o = @cp.get_owner(@import_owner['key'])
    o['upstreamUuid'].should be_nil

    # Make sure this still exists:
    custom_sub = @cp.get_subscription(custom_sub['id'])

    # should be able to re-import without an "older than existing" error:
    @cp.import(@import_owner['key'], @export_filename)
    o = @cp.get_owner(@import_owner['key'])
    o['upstreamUuid'].should == @candlepin_client.uuid

    # Delete again and make sure another owner is clear to import the
    # same manifest:
    @import_owner_client.undo_import(@import_owner['key'])
    another_owner = @cp.create_owner(random_string('testowner'))
    @cp.import(another_owner['key'], @export_filename)
    @cp.delete_owner(another_owner['key'])
    @cp.delete_subscription(custom_sub['id'])

    # Re-import so the rest of the tests can pass:
    @cp.import(@import_owner['key'], @export_filename)
  end

  it 'should create a SUCCESS record of the import' do
    # Look for at least one valid entry
    @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
      import.status == 'SUCCESS'
    end.should_not be_empty
  end

  it 'should return a 409 on a duplicate import' do
    lambda do
      @cp.import(@import_owner['key'], @export_filename)
    end.should raise_exception RestClient::Conflict
  end

  it 'should create a FAILURE record on a duplicate import' do
    # This is probably bad - relying on the previous test
    # to actually generate this record
    @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
      import.status == 'FAILURE'
    end.should_not be_empty
  end

  it 'should set the correct error status message' do
    # Again - relying on the 409 test to set this - BAD!
    error = @import_owner_client.list_imports(@import_owner['key']).find do |import|
      import.status == 'FAILURE'
    end

    error.statusMessage.should == 'Import is older than existing data'
  end

  it 'should return a success on a force import' do
    # This test must run after a successful import has already occurred.
    @cp.import(@import_owner['key'], @export_filename, {:force => true})
  end

  it 'should allow importing older manifests into another owner' do
    create_candlepin_export()
    older_export = @export_filename
    create_candlepin_export()
    newer_export = @export_filename
    owner1 = @cp.create_owner(random_string("owner1"))
    owner2 = @cp.create_owner(random_string("owner1"))
    @cp.import(owner1['key'], newer_export)
    @cp.import(owner2['key'], older_export)
  end

  it 'should return 400 when importing already used manifest' do
    # export was already imported into another org in the
    # before statement, let's ensure a second import causes
    # the expected error
    owner2 = @cp.create_owner(random_string("owner2"))

    begin
      @cp.import(owner2['key'], @export_filename)
    rescue RestClient::Exception => e
        expected = "This distributor has already been imported by another owner"
        JSON.parse(e.http_body)["displayMessage"].should == expected
        e.http_code.should == 400
    end
  end

  it "should store the subscription's upstream entitlement cert" do
    sublist = @cp.list_subscriptions(@import_owner['key'])
    cert = @cp.get_subscription_cert sublist.first.id
    cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    cert.include?("-----BEGIN RSA PRIVATE KEY-----").should == true
  end
end
