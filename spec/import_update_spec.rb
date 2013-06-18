require 'candlepin_scenarios'

describe 'Candlepin Import Update' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    create_candlepin_export()
    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_owner_client = user_client(@import_owner, random_string('testuser'))
    @cp.import(@import_owner['key'], @export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
    create_candlepin_export_update()
    @cp.import(@import_owner['key'], @export_filename_update)
  end

  after(:all) do
    cleanup_candlepin_export_update()
  end

  it 'should successfully update the import' do
    @sublist.size().should == 4
    new_sublist = @cp.list_subscriptions(@import_owner['key'])
    new_sublist.size().should == 5

    hasChanged = false
    new_sublist.each do |new_sub|
      @sublist.each do |sub|
	if(sub.id == new_sub.id)
          if(sub.certificate.serial.id != new_sub.certificate.serial.id)
            hasChanged = true
          end
        end
      end
    end

    hasChanged.should == true
  end

end
