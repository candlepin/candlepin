#!/usr/bin/env ruby

require 'spec_helper'
require 'candlepin_scenarios'

describe 'Import and Undo Manifest in parallel' do

  include CandlepinMethods

  before (:all) do	#exports generation
    @iu_exporter = ImportUpdateBrandingExporter.new
  end

  after (:all) do
    @iu_exporter.cleanup()
  end

  it 'should (sync) import and undo manifest in parallel successfully - import first' do
    i = 0
    count = 5
    working = true
    begin
      import_owner = create_owner(random_string("import_branding_owner"))
      begin
        thr = concurrent_import_and_undo_manifest(import_owner, @iu_exporter)

        if time_limit_on_thread_expired(thr)
          puts "Replication No. #{i} - fail (time limit on thread expired)"
          working = false
          next
        end
      rescue Exception => e
        puts "Replication No. #{i} - fail: #{e}"
        puts e.backtrace
        working = false
        next
      end
      i += 1
      delete_owner(import_owner)
    end while (working && i < count)

    working.should be true
  end

  it 'should (sync) import and undo manifest in parallel successfully - undo first' do
    i = 0
    count = 5
    working = true
    begin
      import_owner = create_owner(random_string("import_branding_owner"))
      begin
        thr = concurrent_undo_and_import_manifest(import_owner, @iu_exporter)

        if time_limit_on_thread_expired(thr)
          puts "Replication No. #{i} - fail (time limit on thread expired)"
          working = false
          next
        end
      rescue Exception => e
        puts "Replication No. #{i} - fail: #{e}"
        puts e.backtrace
        working = false
        next
      end
      i += 1
      delete_owner(import_owner)
    end while (working && i < count)

    working.should be true
  end
  # === Classes ====================================================
  class UndoImportException < RuntimeError
  end

  # ===  Util Methods ==============================================

  def concurrent_import_and_undo_manifest(import_owner, iu_exporter)
    @cp.import(import_owner['key'], iu_exporter.export1_filename)
    t1 = Thread.new do
      @cp.import(import_owner['key'], iu_exporter.export2_filename)
    end
    undo_job = @cp.undo_import(import_owner['key'])

    wait_for_undo_job(undo_job) #async job
    thr = t1.join

    return thr
  end

  def wait_for_undo_job(job)
    r = wait_for_job(job['id'], 10) #undo_import can be still running becase is async,
                                    #otherwise '409 Conflict - concurrent modification' can occur

    status = @cp.get_job(job["id"])
    if status["state"] != "FINISHED"
      raise UndoImportException, "UndoImportsJob didn't finish, instead it has state = #{status['state']}"
    end
  end

  def time_limit_on_thread_expired(thr)
    return (thr == nil)
  end

  def concurrent_undo_and_import_manifest(import_owner, iu_exporter)
    @cp.import(import_owner['key'], iu_exporter.export1_filename)
    undo_job = nil
    t1 = Thread.new do
       undo_job = @cp.undo_import(import_owner['key'])
    end
    @cp.import(import_owner['key'], iu_exporter.export2_filename)

    wait_for_undo_job(undo_job) #async job
    thr = t1.join

    return thr
  end
  # -----------------------------------------------------------
end
