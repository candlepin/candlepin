require 'spec_helper'
require 'candlepin_scenarios'

describe 'Suspend mode' do
  include CandlepinMethods

  after(:each) do
    resetMode
  end

  def resetMode
    @cp.post('/status/mode?mode=NORMAL&reason=QPID_UP')
  end

  def changeMode(mode)
    @cp.post("/status/mode?mode=#{mode}&reason=QPID_DOWN")
  end

  it 'should provide /status/mode endpoint' do
    mode = @cp.get('/status/mode')
    expect(mode).to have_key("mode")
    expect(mode["mode"]).to eq("NORMAL")
    changeMode("SUSPEND")

    mode = @cp.get('/status/mode')
    expect(mode).to have_key("mode")
    expect(mode).to have_key("reason")
 
    expect(mode["mode"]).to eq("SUSPEND")
    expect(mode["reason"]).to eq("QPID_DOWN")
  end

  it 'should not accept requests when in SUSPEND mode' do
    changeMode("SUSPEND")
    begin
      create_owner random_string('test_owner')
    rescue RestClient::ServiceUnavailable => un
      displayMessage = JSON.parse(un.response.body)["displayMessage"]
      displayMessage.should == 'Candlepin is in Suspend mode, please check /status/mode resource to get more details'
    end
    expect(@cp.get_status).not_to be_empty
  end

  it 'should start receiving request when leaving SUSPEND mode' do
    changeMode("SUSPEND")
    expect { create_owner random_string('test_owner') }.to raise_error(RestClient::ServiceUnavailable)
    expect(@cp.get_status).not_to be_empty

    changeMode("NORMAL")
    expect(create_owner random_string('test_owner')).to have_key("created")
  end
end
