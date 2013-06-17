require 'candlepin_scenarios'
require 'json'

describe 'Candlepin Import' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    @users = []
    create_candlepin_export()

    @cp.unregister @candlepin_consumer['uuid']

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
    pools.length.should == 4
  end

  it 'ignores multiplier for pool quantity' do
    pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
    pools.length.should == 4

    # 1 product has a multiplier of 2 upstream, the other 1.
    # 1 entitlement is consumed from each pool for the export, so
    # quantity should be 1 on both.
    pools[0]['quantity'].should == 1
    pools[1]['quantity'].should == 1
  end

  it 'modifies owner to reference upstream consumer' do
    o = @cp.get_owner(@import_owner['key'])
    o.upstreamConsumer.uuid.should == @candlepin_client.uuid
  end

  it "originating information should be populated in the import" do
    @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
      consumer = @candlepin_consumer
      import['generatedBy'].should == consumer['name']
      import['generatedDate'].should_not be_nil
      import['upstreamId'].should == consumer['uuid']
      import['fileName'].should == @export_filename.split("/").last
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
    o['upstreamConsumer'].should be_nil

    # Make sure this still exists:
    custom_sub = @cp.get_subscription(custom_sub['id'])

    # should be able to re-import without an "older than existing" error:
    @cp.import(@import_owner['key'], @export_filename)
    o = @cp.get_owner(@import_owner['key'])
    o['upstreamConsumer']['uuid'].should == @candlepin_client.uuid

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

  it 'should create a DELETE record on a deleted import' do
    @import_owner_client.undo_import(@import_owner['key'])
    @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
      import.status == 'DELETE'
    end.should_not be_empty
    # Re-import so the rest of the tests can pass:
    @cp.import(@import_owner['key'], @export_filename)
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

    error.statusMessage.should == 'Import is the same as existing data'
  end

  it 'should allow forcing the same manifest' do
    # This test must run after a successful import has already occurred.
    @cp.import(@import_owner['key'], @export_filename,
      {:force => ["MANIFEST_SAME", "DISTRIBUTOR_CONFLICT"]})
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

  it 'should return 409 when importing manifest from different subscription management application' do
    create_candlepin_export()
    another_export = @export_filename
    old_upstream_uuid = @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid']

    begin
      @cp.import(@import_owner['key'], another_export)
    rescue RestClient::Exception => e
        expected = "Owner has already imported from another subscription management application."
        json = JSON.parse(e.http_body)
        json["displayMessage"].include?(expected).should be_true
        json["conflicts"].size.should == 1
        json["conflicts"].include?("DISTRIBUTOR_CONFLICT").should be_true
        e.http_code.should == 409
    end
    @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid'].should == old_upstream_uuid

    # Try again and make sure we don't see MANIFEST_SAME appear: (this was a bug)
    begin
      @cp.import(@import_owner['key'], another_export)
    rescue RestClient::Exception => e
        json = JSON.parse(e.http_body)
        json["conflicts"].size.should == 1
        json["conflicts"].include?("DISTRIBUTOR_CONFLICT").should be_true
    end
  end

  it 'should allow forcing a manifest from a different subscription management application' do
    create_candlepin_export()
    another_export = @export_filename

    old_upstream_uuid = @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid']
    pools = @cp.list_owner_pools(@import_owner['key'])
    pool_ids = pools.collect { |p| p['id'] }
    @cp.import(@import_owner['key'], another_export,
      {:force => ['DISTRIBUTOR_CONFLICT']})
    @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid'].should_not == old_upstream_uuid
    pools = @cp.list_owner_pools(@import_owner['key'])
    new_pool_ids = pools.collect { |p| p['id'] }
    # compare without considering order, pools should have changed completely:
    new_pool_ids.should_not =~ pool_ids
  end

  it 'should import arch content correctly' do
      contents = @cp.list_content()
      contents.each do |content|
        if content.has_key('content_url')
          if content['content_url'] == '/path/to/arch/specific/content'
              content['arches'].should == ['i386', 'x86_64']
              pp "#{content['label']}"
              pp "#{content['arches']}"
          end
        end
      end
  end

  it 'should return 400 when importing manifest in use by another owner' do
    # Because the previous tests put the original import into a different state
    # than if you just run this single one, we need to clear first and then
    # re-import the original.
    # Also added the confirmation that the exception occurs when importing to
    # another owner.
    @import_owner_client.undo_import(@import_owner['key'])
    @cp.import(@import_owner['key'], @export_filename)

    owner2 = @cp.create_owner(random_string("owner2"))
    exception = false
    begin
      @cp.import(owner2['key'], @export_filename)
    rescue RestClient::Exception => e
        expected = "This subscription management application has already been imported by another owner."
        JSON.parse(e.http_body)["displayMessage"].should == expected
        e.http_code.should == 400
        exception = true
    end
    exception.should == true
  end

  it "should store the subscription upstream entitlement cert" do
    sublist = @cp.list_subscriptions(@import_owner['key'])
    cert = @cp.get_subscription_cert sublist.first.id
    cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    cert.include?("-----BEGIN RSA PRIVATE KEY-----").should == true

    # while were here, lets access the upstream cert via entitlement id
    pools =  @import_owner_client.list_pools({:owner => @import_owner['id']})
    pool = pools.find_all {|p| p.subscriptionId == sublist.first.id}[0]
    consumer = consumer_client(@import_owner_client, 'system6')
    entitlement = consumer.consume_pool(pool.id)[0]
    ent =  @cp.get_subscription_cert_by_ent_id entitlement.id
    cert.should == ent
  end

  it 'contains upstream consumer' do
    # this information used to be on /imports but now exists on Owner
    consumer = @candlepin_consumer
    upstream = @cp.get_owner(@import_owner['key'])['upstreamConsumer']
    upstream.uuid.should == consumer['uuid']
    upstream.include?('apiUrl').should be_true
    upstream.id.should_not be_nil
    upstream.idCert.should_not be_nil
    upstream.name.should == consumer['name']
    # upstream.type caused a failure on some machines
    upstream['type'].should == consumer['type']
  end
  
  it 'should contain all sub product data' do
    pool = @cp.list_pools(:owner => @import_owner.id, :product => @product3.id)[0]
    pool.should_not be_nil
    pool["subProductId"].should == @sub_product.id
    pool["subProvidedProducts"].length.should == 1
    pool["subProvidedProducts"][0]["productId"].should == @sub_provided_prod.id
  end
end
