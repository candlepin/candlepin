require 'spec_helper'
require 'candlepin_scenarios'

describe 'Import Into Environment', :serial => true do

  include CandlepinMethods

  before(:all) do
    @exporters = []

    @cp = Candlepin.new('admin', 'admin')
    skip("candlepin running in hosted mode") if is_hosted?
  end

  after(:all) do
    @exporters.each do |e|
      e.cleanup()
    end
  end

  it 'should not remove custom pools during import' do
    cp_export = StandardExporter.new
    cp_export.create_candlepin_export()
    cp_export_file = cp_export.export_filename
    @cp_correlation_id = "a7b79f6d-63ca-40d8-8bfb-f255041f4e3b"

    candlepin_consumer = cp_export.candlepin_client.get_consumer()
    candlepin_consumer.unregister candlepin_consumer['uuid']

    @exporters << cp_export

    import_owner = create_owner(random_string("test_owner"))
    import_username = random_string("import-user")
    import_owner_client = user_client(import_owner, import_username)

    product1 = create_product('custom_prod-1', 'custom_prod-1', :owner => import_owner['key'])
    product2 = create_product('custom_prod-2', 'custom_prod-2', :owner => import_owner['key'])
    custom_prod_pool_1 = @cp.create_pool(import_owner['key'], product1.id, { :quantity => 10 })
    custom_prod_pool_2 = @cp.create_pool(import_owner['key'], product2.id, { :quantity => 10 })

    expect(custom_prod_pool_1).to_not be_nil
    expect(custom_prod_pool_2).to_not be_nil

    import_record = @cp.import(import_owner['key'], cp_export_file)
    import_record.status.should == 'SUCCESS'
    import_record.statusMessage.should == "#{import_owner['key']} file imported successfully."

    # The custom pools should *not* have been deleted during the import
    pool1 = @cp.get_pool(custom_prod_pool_1['id'])
    pool2 = @cp.get_pool(custom_prod_pool_2['id'])

    expect(pool1).to_not be_nil
    expect(pool2).to_not be_nil
  end

  it 'should not remove pools for custom subscriptions during import' do
    cp_export = StandardExporter.new
    cp_export.create_candlepin_export()
    cp_export_file = cp_export.export_filename
    @cp_correlation_id = "a7b79f6d-63ca-40d8-8bfb-f255041f4e3b"

    candlepin_consumer = cp_export.candlepin_client.get_consumer()
    candlepin_consumer.unregister candlepin_consumer['uuid']

    @exporters << cp_export

    import_owner = create_owner(random_string("test_owner"))
    import_username = random_string("import-user")
    import_owner_client = user_client(import_owner, import_username)

    product1 = create_product('custom_prod-1', 'custom_prod-1', :owner => import_owner['key'])
    product2 = create_product('custom_prod-2', 'custom_prod-2', :owner => import_owner['key'])
    custom_sub_pool_1 = @cp.create_pool(import_owner['key'], product1.id, {:quantity => 10})
    custom_sub_pool_2 = @cp.create_pool(import_owner['key'], product2.id, {:quantity => 10})

    expect(custom_sub_pool_1).to_not be_nil
    expect(custom_sub_pool_2).to_not be_nil

    import_record = @cp.import(import_owner['key'], cp_export_file)
    import_record.status.should == 'SUCCESS'
    import_record.statusMessage.should == "#{import_owner['key']} file imported successfully."

    # The custom pools should *not* have been deleted during the import
    pool1 = @cp.get_pool(custom_sub_pool_1['id'])
    pool2 = @cp.get_pool(custom_sub_pool_2['id'])

    expect(pool1).to_not be_nil
    expect(pool2).to_not be_nil
  end

end
