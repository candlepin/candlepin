require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Certificate' do
  include CandlepinMethods

  class String
      def to_date
          return Date.strptime(self, "%Y-%m-%d")
      end
  end

  def change_dt_and_qty
      sub = @cp.list_subscriptions(@owner['key'])[0]
      sub.endDate = sub.endDate.to_date + 10
      sub.startDate = sub.startDate.to_date - 10
      sub.quantity = sub.quantity + 10

      @cp.update_subscription(sub)
      @cp.refresh_pools(@owner['key'])
      return sub
  end

  before(:each) do
    @owner = create_owner random_string('test_owner')
    monitoring = create_product()

    @cp.create_subscription(@owner['key'], monitoring.id, 10)
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('billy'))

    @system = consumer_client(@user, random_string('system1'))
    @entitlement = @system.consume_product(monitoring.id)[0]
  end

  it 'is available after consuming an entitlement' do
    @system.list_certificates.length.should == 1
  end

  it 'can be manually regenerated for a consumer' do
    @system.list_certificates.length.should == 1
    old_certs = @system.list_certificates()
    @system.regenerate_entitlement_certificates()

    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0
  end

  it 'can regenerate certificate by entitlement id' do
    @system.list_certificates.length.should == 1
    old_certs = @system.list_certificates()
    ents = @system.list_entitlements()

    @system.regenerate_entitlement_certificates_for_entitlement(ents[0].id)
    new_certs = @system.list_certificates()
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0

  end

  it 'can be manually regenerated for a product' do
    coolapp = create_product
    @cp.create_subscription(@owner['key'], coolapp.id, 10)
    @cp.refresh_pools(@owner['key'])
    @system.consume_product coolapp.id

    @system.list_certificates.length.should == 2
    old_certs = @system.list_certificates()

    @cp.regenerate_entitlement_certificates_for_product(coolapp.id)

    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    # System has two certs, but we only regenerated for one product, so the
    # other serial should have remained the same:
    (old_ids & new_ids).size.should == 1
  end

  it 'will be regenerated when changing existing subscription\'s end date' do
    sub = @cp.list_subscriptions(@owner['key'])[0]
    sub.endDate = sub.endDate.to_date + 2
    old_cert = @system.list_certificates()[0]
    @cp.update_subscription(sub)

    @cp.refresh_pools(@owner['key'])

    new_cert = @system.list_certificates()[0]
    old_cert.serial.id.should_not == new_cert.serial.id

    ent = @system.get_entitlement(@entitlement.id)
    sub.endDate.should == ent.endDate.to_date
  end

  it 'single entitlement in excess will be deleted when existing subscription quantity is decreased' do
      # this entitlement makes the counts inconclusive
      @system.unbind_entitlement(@entitlement.id)
      prod = create_product(nil, nil, {:attributes => {"multi-entitlement" => "yes"}})
      sub = @cp.create_subscription(@owner['key'], prod.id, 10)
      @cp.refresh_pools(@owner['key'])
      pool = @system.list_pools({:owner => @owner['id'], :product => prod['id']})[0]

      @system.consume_pool(pool['id'], {:quantity => 6})
      @system.list_certificates().size.should == 1
      sub.quantity = sub.quantity.to_i - 5
      @cp.update_subscription(sub)

      @cp.refresh_pools(@owner['key'])

      @system.list_certificates().size.should == 0
  end

  it 'multiple entitlement in excess will be deleted when existing subscription quantity is decreased' do
      # this entitlement makes the counts inconclusive
      @system.unbind_entitlement(@entitlement.id)
      prod = create_product(nil, nil, {:attributes => {"multi-entitlement" => "yes"}})
      sub = @cp.create_subscription(@owner['key'], prod.id, 10)
      @cp.refresh_pools(@owner['key'])
      pool = @system.list_pools({:owner => @owner['id'], :product => prod['id']})[0]

      for i in 0..4
          @system.consume_pool(pool['id'], {:quantity => 2})
      end
      @system.list_certificates().size.should == 5

      sub.quantity = sub.quantity.to_i - 5
      @cp.update_subscription(sub)
      @cp.refresh_pools(@owner['key'])
      @system.list_certificates().size.should == 2
  end

  it 'will be regenerated when existing subscription\'s quantity and dates are changed' do
      old_cert = @system.list_certificates()[0]
      change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      old_cert.serial.id.should_not == new_cert.serial.id
  end

  it 'will be regenerated and dates will have the same values as that of the subscription which was changed' do
      sub = change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      x509 = OpenSSL::X509::Certificate.new(new_cert['cert'])
      sub['startDate'].should == x509.not_before().strftime('%Y-%m-%d').to_date
      sub['endDate'].should == x509.not_after().strftime('%Y-%m-%d').to_date
  end

  it "won't let one consumer regenerate another's certificates" do
    @system.list_certificates.length.should == 1
    @system2 = consumer_client(@user, random_string('system2'))

    lambda do
      @system2.put("/consumers/#{@system.uuid}/certificates")
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "won't let one consumer regenerate another's certificate by entitlement" do
    @system.list_certificates.length.should == 1
    ents = @system.list_entitlements
    @system2 = consumer_client(@user, random_string('system2'))

    lambda do
      @system2.regenerate_entitlement_certificates_for_entitlement(ents.first.id, @system.uuid)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

end
