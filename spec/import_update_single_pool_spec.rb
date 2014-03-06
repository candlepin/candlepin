require 'spec_helper'
require 'candlepin_scenarios'

describe 'Import Single Pool Update', :serial => true do

  include CandlepinMethods

  class ImportUpdateExporter < Exporter
    attr_reader :pool
    attr_reader :entitlement1

    def initialize
      super()
      product = create_product(random_string(), random_string())
      end_date = Date.new(2025, 5, 29)
      @cp.create_subscription(@owner['key'], product.id, 200, [], '', '12345', '6789', nil, end_date)

      @cp.refresh_pools(@owner['key'])
      @pool = @cp.list_pools(:owner => @owner.id, :product => product.id)[0]
      @entitlement1 = @candlepin_client.consume_pool(@pool.id, {:quantity => 15})[0]
    end
  end

  before(:all) do
    @exporter = ImportUpdateExporter.new
  end

  after(:all) do
    @exporter.cleanup()
  end

  it 'should be able to maintain multiple imported entitlements from the same pool' do
    export_filename = @exporter.create_candlepin_export.export_filename
    import_owner = create_owner(random_string("test_owner"))

    @cp.import(import_owner['key'], export_filename)
    sublist = @cp.list_subscriptions(import_owner['key'])
    sublist.size().should == 1

    entitlement2 = @exporter.candlepin_client.consume_pool(@exporter.pool.id, {:quantity => 25})[0]
    export_filename = @exporter.create_candlepin_export.export_filename
    @cp.import(import_owner['key'], export_filename)
    sublist = @cp.list_subscriptions(import_owner['key'])
    sublist.size().should == 2

    @exporter.candlepin_client.unbind_entitlement(@exporter.entitlement1.id)
    export_filename = @exporter.create_candlepin_export.export_filename
    @cp.import(import_owner['key'], export_filename)
    sublist = @cp.list_subscriptions(import_owner['key'])
    sublist.size().should == 1
    sublist[0]['quantity'].should == 25
    sublist[0]['upstreamEntitlementId'].should == entitlement2.id
  end
end
