require 'spec_helper'
require 'candlepin_scenarios'

describe 'Import Update', :serial => true do

  include CandlepinMethods

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')
    skip("candlepin running in hosted mode") if is_hosted?
    @exporter = VirtLimitExporter.new
    base_export = @exporter.create_candlepin_export()

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_username = random_string("import-user")
    @import_owner_client = user_client(@import_owner, @import_username)
    @cp.import(@import_owner['key'], base_export.export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
  end

  after(:all) do
    @cp.delete_user(@import_username) if @import_username
    @cp.delete_owner(@import_owner['key']) if @import_owner
    @exporter.cleanup() if @exporter
  end

  it 'should successfully update the import' do
    @user = user_client(@import_owner, random_string('user'))
    @host1 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host1_client = Candlepin.new(nil, nil, @host1['idCert']['cert'], @host1['idCert']['key'])
    all_pools = @user.list_pools :owner => @import_owner.id
    normal_pool = all_pools.select{|i| i['type']=='NORMAL'}[0]
    all_pools.size.should == 2
    @host1_client.consume_pool(normal_pool.id, {:quantity => 2})
    all_pools = @user.list_pools :owner => @import_owner.id
    all_pools.size.should == 3

    # Sleep to create a gap between when the manifest was initially imported
    sleep 1
    # Now lets import
    updated_export = @exporter.create_candlepin_export_update()
    @cp.import(@import_owner['key'], updated_export.export_filename)

  end

end
