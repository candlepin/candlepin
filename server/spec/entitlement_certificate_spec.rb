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
      pool = @cp.list_owner_pools(@owner['key'])[0]

      pool.endDate = pool.endDate.to_date + 10
      pool.startDate = pool.startDate.to_date - 10
      pool.quantity = pool.quantity + 10

      @cp.update_pool(@owner['key'], pool)
      return @cp.get_pool(pool['id'])
  end

  before(:each) do
    @owner = create_owner random_string('test_owner')
    monitoring = create_product()

    @pool = @cp.create_pool(@owner['key'], monitoring.id, {:quantity => 10})

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
    @cp.create_pool(@owner['key'], coolapp.id, {:quantity => 10})
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
    @pool.endDate = @pool.endDate.to_date + 2
    old_cert = @system.list_certificates()[0]

    @cp.update_pool(@owner['key'], @pool)
    updated = @cp.get_pool(@pool['id'])
    expect(updated['endDate'].to_date).to eq(@pool.endDate)

    new_cert = @system.list_certificates().first
    expect(old_cert.serial.id).to_not eq(new_cert.serial.id)

    ent = @system.get_entitlement(@entitlement.id)
    expect(ent.endDate).to eq(updated.endDate)
  end

  it 'single entitlement in excess will be deleted when existing subscription quantity is decreased' do
      # this entitlement makes the counts inconclusive
      @system.unbind_entitlement(@entitlement.id)
      prod = create_product(nil, nil, {:attributes => {"multi-entitlement" => "yes"}})
      pool = @cp.create_pool(@owner['key'], prod.id, {:quantity => 10})

      @system.consume_pool(pool['id'], {:quantity => 6})
      @system.list_certificates().size.should == 1

      pool.quantity = pool.quantity.to_i - 5
      @cp.update_pool(@owner['key'], pool)

      @system.list_certificates().size.should == 0
  end

  it 'multiple entitlement in excess will be deleted when existing subscription quantity is decreased' do
      # this entitlement makes the counts inconclusive
      @system.unbind_entitlement(@entitlement.id)
      prod = create_product(nil, nil, {:attributes => {"multi-entitlement" => "yes"}})
      pool = @cp.create_pool(@owner['key'], prod.id, {:quantity => 10})

      for i in 0..4
          @system.consume_pool(pool['id'], {:quantity => 2})
      end

      @system.list_certificates().size.should == 5
      pool.quantity = pool.quantity.to_i - 5
      @cp.update_pool(@owner['key'], pool)
      @system.list_certificates().size.should == 2
  end

  it 'will be regenerated when existing subscription\'s quantity and dates are changed' do
      old_cert = @system.list_certificates()[0]
      change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      old_cert.serial.id.should_not == new_cert.serial.id
  end

  it 'will be regenerated and dates will have the same values as that of the subscription which was changed' do
      pool = change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      x509 = OpenSSL::X509::Certificate.new(new_cert['cert'])

      expect(pool['startDate'].to_date).to eq(x509.not_before().to_date)
      expect(pool['endDate'].to_date).to eq(x509.not_after().to_date)
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

  it "regenerates entitlement certificates by product for all owners" do
    owner1 = create_owner(random_string("test_owner_1"))
    owner2 = create_owner(random_string("test_owner_2"))
    owner3 = create_owner(random_string("test_owner_3"))

    prod_id = "test_prod"
    safe_prod_id = "safe_prod"

    prod1 = create_product(prod_id, "test product", {:owner => owner1['key']})
    safe_prod1 = create_product(safe_prod_id, "safe product", {:owner => owner1['key']})
    prod2 = create_product(prod_id, "test product", {:owner => owner2['key']})
    safe_prod2 = create_product(safe_prod_id, "safe product", {:owner => owner2['key']})
    prod3 = create_product(prod_id, "test product", {:owner => owner3['key']})
    safe_prod3 = create_product(safe_prod_id, "safe product", {:owner => owner3['key']})

    @cp.create_pool(owner1['key'], prod1.id, {:quantity => 10})
    @cp.create_pool(owner1['key'], safe_prod1.id, {:quantity => 10})
    @cp.create_pool(owner2['key'], prod2.id, {:quantity => 10})
    @cp.create_pool(owner2['key'], safe_prod2.id, {:quantity => 10})
    @cp.create_pool(owner3['key'], prod3.id, {:quantity => 10})
    @cp.create_pool(owner3['key'], safe_prod3.id, {:quantity => 10})

    user1 = user_client(owner1, random_string('user1'))
    user2 = user_client(owner2, random_string('user2'))
    user3 = user_client(owner3, random_string('user3'))

    system1 = consumer_client(user1, random_string('system1'))
    system2 = consumer_client(user2, random_string('system2'))
    system3 = consumer_client(user3, random_string('system3'))

    system1.consume_product(prod1.id)
    system1.consume_product(safe_prod1.id)
    system2.consume_product(prod2.id)
    system2.consume_product(safe_prod2.id)
    system3.consume_product(prod3.id)
    system3.consume_product(safe_prod3.id)

    sys1_old_certs = system1.list_certificates()
    sys2_old_certs = system2.list_certificates()
    sys3_old_certs = system3.list_certificates()
    sys1_old_certs.length.should == 2
    sys2_old_certs.length.should == 2
    sys3_old_certs.length.should == 2

    result = @cp.regenerate_entitlement_certificates_for_product(prod_id)

    sys1_new_certs = system1.list_certificates()
    sys2_new_certs = system2.list_certificates()
    sys3_new_certs = system3.list_certificates()
    sys1_new_certs.length.should == 2
    sys2_new_certs.length.should == 2
    sys3_new_certs.length.should == 2

    # Cert IDs should have changed across the board for prod, but safe_prod should remain untouched.
    sys1_old_ids = sys1_old_certs.map { |cert| cert['serial']['id'] }
    sys1_new_ids = sys1_new_certs.map { |cert| cert['serial']['id'] }
    (sys1_old_ids & sys1_new_ids).size.should == 1

    sys2_old_ids = sys2_old_certs.map { |cert| cert['serial']['id'] }
    sys2_new_ids = sys2_new_certs.map { |cert| cert['serial']['id'] }
    (sys2_old_ids & sys2_new_ids).size.should == 1

    sys3_old_ids = sys3_old_certs.map { |cert| cert['serial']['id'] }
    sys3_new_ids = sys3_new_certs.map { |cert| cert['serial']['id'] }
    (sys3_old_ids & sys3_new_ids).size.should == 1
  end

end
