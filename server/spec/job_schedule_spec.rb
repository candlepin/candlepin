require 'spec_helper'
require 'candlepin_scenarios'

describe 'Scheduled Jobs' do

  include CandlepinMethods

  it 'should not schedule arbitrary tasks' do
    lambda {
      job = @cp.trigger_job('MySQLInjectionJob')
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should not schedule non cron tasks' do
    lambda {
      job = @cp.trigger_job('UndoImportsJob')
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should schedule cron tasks irrespective of the case' do
    job = @cp.trigger_async_job('EXPIRED_POOLS_CLEANUP')
    expect(job['state']).to eq('QUEUED')

    wait_for_async_job(job['id'], 15)
  end

  it 'should purge expired pools' do
    @owner = create_owner random_string 'test_owner'
    @monitoring_prod = create_product(nil, 'monitoring', :attributes => { 'variant' => "Satellite Starter Pack" })

    now = DateTime.now
    pool = @cp.create_pool(@owner['key'], @monitoring_prod.id, {
      :quantity => 6,
      :start_date => now - 20,
      :end_date => now - 10
    });
    #verify pool exists before
    @cp.get_pool(pool['id']).should_not be_nil
    job = @cp.trigger_async_job('EXPIRED_POOLS_CLEANUP')
    wait_for_async_job(job['id'], 15)
    lambda {
      pool = @cp.get_pool(pool['id'])
      pp pool
    }.should raise_exception(RestClient::ResourceNotFound)
  end

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
    job = @cp.trigger_async_job('IMPORT_RECORD_CLEANER')
    wait_for_async_job(job['id'], 15)
    records = @cp.list_imports(@import_owner['key'])
    records.size.should == 10
  end

end
