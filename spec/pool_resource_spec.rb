require 'spec_helper'
require 'candlepin_scenarios'

describe 'Pool Resource' do

  include CandlepinMethods

  it 'includes null values in json' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product(nil, nil, :owner => owner1['key'])
    create_pool_and_subscription(owner1['key'], product.id, 10)
    @cp.refresh_pools(owner1['key'])
    pool = owner1_client.list_pools(:owner => owner1.id).first
    pool.member?("upstreamPoolId").should == true
    pool["upstreamPoollId"].should be_nil
    pool.member?("upstreamEntitlementId").should == true
    pool["upstreamEntitlementId"].should be_nil
    pool.member?("upstreamConsumerId").should == true
    pool["upstreamConsumerId"].should be_nil
  end

  it 'should allow fetching content delivery network by pool id' do
    skip("candlepin running in hosted mode") if is_hosted?
    cdn_label = random_string("test-cdn")
    cdn = create_cdn(cdn_label, "Test CDN", "https://cdn.test.com")
    cdn.id.should_not be nil
    @opts = {"cdn_label"=> cdn_label}
    @cp_export = StandardExporter.new
    @cp_export.create_candlepin_export()
    @cp_export_file = @cp_export.export_filename
    @import_owner = @cp.create_owner(random_string("test_owner"))
    import_record = @cp.import(@import_owner['key'], @cp_export_file)
    pools = @cp.list_owner_pools(@import_owner['key'])
    # only master pools have cdns associated with them
    pools = pools.select do |pool|
        pool['type'] == 'NORMAL'
    end
    pools.each do |pool|
        pool.cdn.should be nil
        result_cdn = @cp.get_cdn_from_pool(pool['id'])
        result_cdn.name.should == cdn.name
        result_cdn.url.should == cdn.url
    end
    @cp.delete_owner(@import_owner['key'])
    @cp_export.cleanup
  end

end
