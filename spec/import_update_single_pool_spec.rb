require 'candlepin_scenarios'

describe 'Candlepin Import Update' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  it 'should be able to maintain multiple imported entitlements from the same pool' do
    @cp = Candlepin.new('admin', 'admin')
    @owner = @cp.create_owner(random_string('test_owner'))
    @user = @cp.create_user(random_string('test_user'), 'password')
    Candlepin.new(@user['username'], 'password')
    # Create a role for user to administer the given owner:
    role = create_role(nil, @owner['key'], 'ALL')
    @cp.add_role_user(role['id'], @user['username'])
    owner_client = Candlepin.new(@user['username'], 'password')

    product = @cp.create_product(random_string(), random_string())
    @end_date = Date.new(2025, 5, 29)

    sub = @cp.create_subscription(@owner['key'], product.id, 200, [], '', '12345', '6789', nil, @end_date)
    @cp.refresh_pools(@owner['key'])

    pool = @cp.list_pools(:owner => @owner.id, :product => product.id)[0]

    @candlepin_client = consumer_client(owner_client, random_string('test_client'),
        "candlepin", @user['username'])
    @entitlement1 = @candlepin_client.consume_pool(pool.id, {:quantity => 15})[0]
    #print @entitlement1

    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    @export_dir = File.join(@tmp_dir, "export")
    Dir.mkdir(@tmp_dir)

    @export_filename = @candlepin_client.export_consumer(@tmp_dir)
    # Save current working dir so we can return later:
    @orig_working_dir = Dir.pwd()

    File.exist?(@export_filename).should == true
    unzip_export_file(@export_filename, @tmp_dir)
    unzip_export_file(File.join(@tmp_dir, "consumer_export.zip"), @tmp_dir)

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_owner_client = user_client(@import_owner, random_string('testuser'))
    @cp.import(@import_owner['key'], @export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
    @sublist.size().should == 1


    @entitlement2 = @candlepin_client.consume_pool(pool.id, {:quantity => 25})[0]

    # clean previous export stuff
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir)
    FileUtils.rm_rf(@export_dir)

    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    @export_dir = File.join(@tmp_dir, "export")
    Dir.mkdir(@tmp_dir)

    @export_filename = @candlepin_client.export_consumer(@tmp_dir)
    # Save current working dir so we can return later:
    @orig_working_dir = Dir.pwd()

    File.exist?(@export_filename).should == true
    unzip_export_file(@export_filename, @tmp_dir)
    unzip_export_file(File.join(@tmp_dir, "consumer_export.zip"), @tmp_dir)

    @cp.import(@import_owner['key'], @export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
    @sublist.size().should == 2

    # clean previous export stuff
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir)
    FileUtils.rm_rf(@export_dir)

    @candlepin_client.unbind_entitlement(@entitlement1.id)
    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    @export_dir = File.join(@tmp_dir, "export")
    Dir.mkdir(@tmp_dir)

    @export_filename = @candlepin_client.export_consumer(@tmp_dir)
    # Save current working dir so we can return later:
    @orig_working_dir = Dir.pwd()

    File.exist?(@export_filename).should == true
    unzip_export_file(@export_filename, @tmp_dir)
    unzip_export_file(File.join(@tmp_dir, "consumer_export.zip"), @tmp_dir)

    @cp.import(@import_owner['key'], @export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
    @sublist.size().should == 1
    @sublist[0]['quantity'].should == 25
    @sublist[0]['upstreamEntitlementId'].should == @entitlement2.id

    # clean previous export stuff
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir)
    FileUtils.rm_rf(@export_dir)

    #this will also delete the owner's users
    @cp.delete_owner(@owner['key'])
  end
end
