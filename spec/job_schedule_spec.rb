require 'spec_helper'
require 'candlepin_scenarios'

describe 'Scheduled Jobs' do

  include CandlepinMethods

  it 'should purge import records' do
    skip("candlepin running in hosted mode") if is_hosted?

    @cp_export = StandardExporter.new
    @cp_export.create_candlepin_export()
    @cp_export_file = @cp_export.export_filename

    @import_owner = @cp.create_owner(random_string("test_owner"))
    (1..11).each do
      import_record = @cp.import(@import_owner['key'],
        @cp_export_file,
        {:force => ["SIGNATURE_CONFLICT", 'MANIFEST_SAME']})
      import_record.status.should == 'SUCCESS'
    end
    records = @cp.list_imports(@import_owner['key'])
    records.size.should == 11
    job = @cp.trigger_job('ImportRecordCleanerJob')
    wait_for_job(job['id'], 15)
    records = @cp.list_imports(@import_owner['key'])
    records.size.should == 10
  end

end
