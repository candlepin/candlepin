require 'spec_helper'
require 'candlepin_scenarios'
require 'json'

describe 'Import Test Group:', :serial => true do

  include CandlepinMethods
  include VirtHelper

  RSpec.shared_examples "importer" do |async|

    before(:all) do
      # Decide the import method to use, async or synchronous
      if async
        @import_method = import_and_wait
      else
        @import_method = import_now
      end

      @cp = Candlepin.new('admin', 'admin')
      skip("candlepin running in hosted mode") if is_hosted?

      @cp_export = async ? AsyncStandardExporter.new : StandardExporter.new
      @cp_export.create_candlepin_export()
      @cp_export_file = @cp_export.export_filename
      @cp_correlation_id = "a7b79f6d-63ca-40d8-8bfb-f255041f4e3a"

      @candlepin_consumer = @cp_export.candlepin_client.get_consumer()
      @candlepin_consumer.unregister @candlepin_consumer['uuid']

      @import_owner = @cp.create_owner(random_string("test_owner"))
      @import_username = random_string("import-user")
      @import_owner_client = user_client(@import_owner, @import_username)
      import_record = @import_method.call(@import_owner['key'], @cp_export_file)
      import_record.status.should == 'SUCCESS'
      import_record.statusMessage.should == "#{@import_owner['key']} file imported successfully."
      @exporters = [@cp_export]
    end

    after(:all) do
      @cp.delete_user(@import_username) if @import_username
      @cp.delete_owner(@import_owner['key']) if @import_owner

      if @exporters
        @exporters.each do |e|
          e.cleanup()
        end
      end
    end

    def import_now
      lambda { |owner_key, export_file, param_map={}|
        @cp.import(owner_key, export_file, param_map)
      }
    end

    def import_and_wait
      lambda { |owner_key, export_file, param_map={}|
        headers = { :correlation_id => @cp_correlation_id }
        job = @cp.import_async(owner_key, export_file, param_map, headers)
        # Wait a little longer here as import can take a bit of time
        wait_for_job(job["id"], 10)
        status = @cp.get_job(job["id"], true)
        if status["state"] == "FAILED"
          raise AsyncImportFailure.new(status)
        end
        status["resultData"]
      }
    end

    it 'creates pools' do
      pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
      pools.length.should == 8

      # Some of these pools must carry provided/derived provided products,
      # don't care which pool just need to be sure that they're getting
      # imported at all:
      provided_found = false
      derived_found = false
      pools.each do |pool|
        if pool['providedProducts'].size > 0
          provided_found = true
        end
        if pool['derivedProvidedProducts'].size > 0
          derived_found = true
        end
      end
      provided_found.should be true
      derived_found.should be true
    end

    it 'ignores multiplier for pool quantity' do
      pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
      pools.length.should == 8
      # 1 product has a multiplier of 2 upstream, the others 1.
      # 1 entitlement is consumed from each pool for the export, so
      # quantity should be 1 on each.
      # remove unmapped guest pool, not part of test
      filter_unmapped_guest_pools(pools)

      pools.each do |p|
        p['quantity'].should == 1
      end
    end

    it 'modifies owner to reference upstream consumer' do
      owner = @cp.get_owner(@import_owner['key'])
      owner.upstreamConsumer.uuid.should == @cp_export.candlepin_client.uuid
    end

    it "originating information should be populated in the import record" do
      @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
        consumer = @candlepin_consumer
        import['generatedBy'].should == consumer['uuid']
        import['generatedDate'].should_not be_nil
        import['fileName'].should == @cp_export_file.split("/").last
        import['upstreamConsumer']['uuid'].should == consumer['uuid']
        import['upstreamConsumer']['name'].should == consumer['name']
        import['upstreamConsumer']['ownerId'].should == @import_owner.id
      end
    end

    it 'can be undone' do
      # Make a custom pool so we can be sure it does not get wiped
      # out during either the undo or a subsequent re-import:
      prod_name = random_string("custom_pool_prod-")
      prod = create_product(prod_name, prod_name, {:owner => @import_owner['key']})
      custom_pool = @cp.create_pool(@import_owner['key'], prod['id'])

      job = @import_owner_client.undo_import(@import_owner['key'])
      wait_for_job(job['id'], 30)

      pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
      pools.length.should == 1 # this is our custom pool
      pools[0]['id'].should == custom_pool['id']

      o = @cp.get_owner(@import_owner['key'])
      o['upstreamConsumer'].should be_nil

      # should be able to re-import without an "older than existing" error:
      @import_method.call(@import_owner['key'], @cp_export_file)
      o = @cp.get_owner(@import_owner['key'])
      o['upstreamConsumer']['uuid'].should == @cp_export.candlepin_client.uuid

      # Delete again and make sure another owner is clear to import the
      # same manifest:
      job = @import_owner_client.undo_import(@import_owner['key'])
      wait_for_job(job['id'], 30)

      # Verify our custom sub still exists
      pools = @import_owner_client.list_pools({:owner => @import_owner['id']})
      pools.length.should == 1 # this is our custom pool
      pools[0]['id'].should == custom_pool['id']

      another_owner = @cp.create_owner(random_string('testowner'))
      @import_method.call(another_owner['key'], @cp_export_file)
      @cp.delete_owner(another_owner['key'])
      @cp.delete_pool(custom_pool['id'])

      # Re-import so the rest of the tests can pass:
      @import_method.call(@import_owner['key'], @cp_export_file)
    end

    it 'should create a SUCCESS record of the import' do
      # Look for at least one valid entry
      @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
        import.status == 'SUCCESS'
      end.should_not be_empty
    end

    it 'should create a DELETE record on a deleted import' do
      job = @import_owner_client.undo_import(@import_owner['key'])
      wait_for_job(job['id'], 30)
      @import_owner_client.list_imports(@import_owner['key']).find_all do |import|
        import.status == 'DELETE'
      end.should_not be_empty
      # Re-import so the rest of the tests can pass:
      @import_method.call(@import_owner['key'], @cp_export_file)
    end

    it 'should return a 409 on a duplicate import' do
      exception = false
      begin
        @import_method.call(@import_owner['key'], @cp_export_file)
      rescue RestClient::Conflict => e
        async.should be false
        json = JSON.parse(e.http_body)
        exception = true
      rescue AsyncImportFailure => aif
        async.should be true
        json = aif.data["resultData"]
        json.should_not be_nil
        exception = true
      end

      exception.should be true
      if async
        json.include?("MANIFEST_SAME").should be true
      else
        expect(json["conflicts"].size).to eq(1)
        json["conflicts"].include?("MANIFEST_SAME").should be true
      end
    end

    it 'should not allow importing an old manifest' do
      owner = create_owner(random_string("test_owner"))
      exporter = StandardExporter.new
      @exporters << exporter
      older = exporter.create_candlepin_export().export_filename

      sleep 2

      newer = exporter.create_candlepin_export().export_filename

      @import_method.call(owner['key'], newer)
      exception = false
      begin
        @import_method.call(owner['key'], older)
      rescue RestClient::Conflict => e
        async.should be false
        json = JSON.parse(e.http_body)
        exception = true
      rescue AsyncImportFailure => aif
        async.should be true
        json = aif.data["resultData"]
        json.should_not be_nil
        exception = true
      end

      exception.should be true
      if async
        json.include?("MANIFEST_OLD").should be true
      else
        expect(json["conflicts"].size).to eq(1)
        json["conflicts"].include?("MANIFEST_OLD").should be true
      end
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
      @import_method.call(@import_owner['key'], @cp_export_file,
        {:force => ["MANIFEST_SAME", "DISTRIBUTOR_CONFLICT"]})
    end

    it 'should allow importing older manifests into another owner' do
      old_exporter = StandardExporter.new
      @exporters << old_exporter
      older = old_exporter.create_candlepin_export().export_filename

      new_exporter = StandardExporter.new
      @exporters << new_exporter
      newer = new_exporter.create_candlepin_export().export_filename

      owner1 = create_owner(random_string("owner1"))
      owner2 = create_owner(random_string("owner2"))
      @import_method.call(owner1['key'], newer)
      @import_method.call(owner2['key'], older)
    end

    it 'should return 409 when importing manifest from different subscription management application' do
      exporter = StandardExporter.new
      @exporters << exporter
      another = exporter.create_candlepin_export().export_filename

      old_upstream_uuid = @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid']
      expected = "Owner has already imported from another subscription management application."
      exception = false;
      begin
        @import_method.call(@import_owner['key'], another)
      rescue RestClient::Exception => e
        async.should be false
        e.http_code.should == 409
        json = JSON.parse(e.http_body)
        exception = true
      rescue AsyncImportFailure => aif
        async.should be true
        json = aif.data["resultData"]
        json.should_not be_nil
        exception = true
      end

      exception.should be true
      if async
        json.include?(expected).should be true
        json.include?("DISTRIBUTOR_CONFLICT").should be true
      else
        json["displayMessage"].include?(expected).should be true
        json["conflicts"].size.should == 1
        json["conflicts"].include?("DISTRIBUTOR_CONFLICT").should be true
      end

      @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid'].should == old_upstream_uuid

      exception = false
      # Try again and make sure we don't see MANIFEST_SAME appear: (this was a bug)
      begin
        @import_method.call(@import_owner['key'], another)
      rescue RestClient::Exception => e
        async.should be false
        e.http_code.should == 409
        json = JSON.parse(e.http_body)
        exception = true
      rescue AsyncImportFailure => aif
        async.should be true
        json = aif.data["resultData"]
        json.should_not be_nil
        exception = true
      end

      exception.should be true
      if async
        json.include?(expected).should be true
        json.include?("DISTRIBUTOR_CONFLICT").should be true
      else
        json["displayMessage"].include?(expected).should be true
        json["conflicts"].size.should == 1
        json["conflicts"].include?("DISTRIBUTOR_CONFLICT").should be true
      end
    end

    it 'should allow forcing a manifest from a different subscription management application' do
      exporter = StandardExporter.new
      @exporters << exporter
      another = exporter.create_candlepin_export().export_filename

      old_upstream_uuid = @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid']
      pools = @cp.list_owner_pools(@import_owner['key'])
      pool_ids = pools.collect { |p| p['id'] }
      @import_method.call(@import_owner['key'], another,
        {:force => ['DISTRIBUTOR_CONFLICT']})
      @cp.get_owner(@import_owner['key'])['upstreamConsumer']['uuid'].should_not == old_upstream_uuid
      pools = @cp.list_owner_pools(@import_owner['key'])
      new_pool_ids = pools.collect { |p| p['id'] }
      # compare without considering order, pools should have changed completely:
      new_pool_ids.should_not =~ pool_ids
    end

    it 'should import arch content correctly' do
        contents = @cp.list_content(@import_owner['key'])
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
      job = @import_owner_client.undo_import(@import_owner['key'])
      wait_for_job(job['id'], 30)

      @import_method.call(@import_owner['key'], @cp_export_file)
      owner2 = @cp.create_owner(random_string("owner2"))
      exception = false
      expected = "This subscription management application has already been imported by another owner."
      begin
        @import_method.call(owner2['key'], @cp_export_file)
      rescue RestClient::Exception => e
        async.should be false
        e.http_code.should == 400
        json = JSON.parse(e.http_body)
        exception = true
      rescue AsyncImportFailure => aif
        async.should be true
        json = aif.data["resultData"]
        json.should_not be_nil
        exception = true
      end

      @cp.delete_owner(owner2['key'])
      exception.should be true
      if async
        json.include?(expected).should be true
      else
        message = json["displayMessage"]
        message.should_not be_nil
        message.should == expected
      end
    end

    it "should store the subscription upstream entitlement cert" do
      sublist = @cp.list_subscriptions(@import_owner['key'])
      # we only want the product that maps to a normal pool
      # i.e. no virt, no multipliers, etc.
      # this is to fix a intermittent test failures when trying
      # to bind to a virt_only or other weird pool
      sub = sublist.find_all {
        |s| s.product.id.start_with?("prod2")
      }

      # use sub.first.id because find_all returns an array, but there
      # can only be one, HIGHLANDER!
      sub.length.should == 1
      pools =  @import_owner_client.list_pools({:owner => @import_owner['id']})
      pool = pools.find_all {
        |p| p.subscriptionId == sub.first.id && p.subscriptionSubKey == "master"
      }[0]
      cert = @cp.get_pool_cert pool.id
      cert[0..26].should == "-----BEGIN CERTIFICATE-----"
      cert.include?("-----BEGIN RSA PRIVATE KEY-----").should == true

      # while were here, lets access the upstream cert via entitlement id
      consumer = consumer_client(@import_owner_client, 'system6')
      entitlement = consumer.consume_pool(pool.id, {:quantity => 1})[0]
      ent =  @cp.get_subscription_cert_by_ent_id entitlement.id
      #ent.cdn['label'].should == @cp_export.cdn_label
      cert.should == ent
    end

    it 'contains upstream consumer' do
      # this information used to be on /imports but now exists on Owner
      # checking for api and webapp overrides
      consumer = @candlepin_consumer

      upstream = @cp.get_owner(@import_owner['key'])['upstreamConsumer']
      upstream.uuid.should == consumer['uuid']
      upstream.id.should_not be_nil
      upstream.name.should == consumer['name']
    end

    it 'should contain all derived product data' do
      pool = @cp.list_pools(:owner => @import_owner.id,
        :product => @cp_export.products[:product3].id)[0]
      pool.should_not be_nil

      pool["derivedProductId"].should == @cp_export.products[:derived_product].id
      pool["derivedProvidedProducts"].length.should == 1
      pool["derivedProvidedProducts"][0]["productId"].should == @cp_export.products[:derived_provided_prod].id
    end

    it 'should contain branding info' do
      pool = @cp.list_pools(:owner => @import_owner.id,
        :product => @cp_export.products[:product1].id)[0]
      pool['branding'].length.should == 1
      pool['branding'][0]['productId'].should == @cp_export.products[:eng_product]['id']
      pool['branding'][0]['name'].should == "Branded Eng Product"
    end

    it 'should put the cdn from the manifest into the created subscriptions' do
      @cp.list_subscriptions(@import_owner['key']).find_all do |sub|
          sub['cdn']['label'].should == @cp_export.cdn_label
      end
    end

  end

  describe "Async Tests" do
    it_should_behave_like "importer", true
  end

  describe "Sync import tests" do
    it_should_behave_like "importer", false
  end

end

class AsyncImportFailure < Exception
  attr_reader :data

  def initialize(data)
    @data = data
  end

end

