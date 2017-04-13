# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'


describe 'DB failfast' do

  include CandlepinMethods

  after(:each) do
    mode = get_mode
    if mode["mode"] == "SUSPEND"
      start_db
      sleep(20)
    end
  end

  def stop_db
    `sudo systemctl stop postgresql || sudo supervisorctl stop postgresql`
  end

  def start_db
    `sudo systemctl start postgresql || sudo supervisorctl start postgresql`
  end

  it 'DB down is detected' do
    assert_mode('NORMAL')

    stop_db
    sleep(11)
    assert_db_state('SUSPEND', 'DOWN')
  end

  it 'DB up is detected' do
    assert_mode('NORMAL')

    stop_db
    sleep(11)
    assert_db_state("SUSPEND", "DOWN")

    start_db
    sleep(20)
    assert_db_state("NORMAL", "UP")
  end

  it 'old DB connections are closed after reconnect' do
    assert_mode('NORMAL')

    stop_db
    sleep(11)
    assert_db_state("SUSPEND", "DOWN")

    start_db
    sleep(20)
    assert_db_state("NORMAL", "UP")
    30.times do
      create_owner random_string("test")
    end
  end

end
