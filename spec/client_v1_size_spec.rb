require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Certificate V1 Size' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @content_list = create_batch_content(200)

    @product1 = create_product(nil, nil, :attributes => {
      :version => '6.4',
      :warning_period => 15,
      :management_enabled => true,
      :virt_only => 'false',
      :support_level => 'standard',
      :support_type => 'excellent',
    })

    @cp.add_content_to_product(@owner['key'], @product1.id, @content_list[0].id, true)

    @cp.create_pool(@owner['key'], @product1.id, {
      :quantity => 10,
      :provided_products => [],
      :contract_number => '12345',
      :account_number => '6789',
      :order_number => 'order1',
      :subscription_id => random_str('source_sub'),
      :upstream_pool_id => random_str('upstream')
    })

    @product2 = create_product(nil, nil, :attributes => {
      :version => '6.4',
      :warning_period => 15,
      :management_enabled => true,
      :virt_only => 'false',
      :support_level => 'standard',
      :support_type => 'excellent',
    })

    @cp.add_content_to_product(@owner['key'], @product2.id, @content_list[0].id, true)

    @cp.create_pool(@owner['key'], @product2.id, {
      :quantity => 1,
      :provided_products => [],
      :contract_number => '12345',
      :account_number => '6789',
      :order_number => 'order1',
      :subscription_id => random_str('source_sub'),
      :upstream_pool_id => random_str('upstream')
    })

    @user = user_client(@owner, random_string('billy'))
    @system = consumer_client(@user, random_string('system1'), :system, nil, {'system.certificate_version' => '1.0'})
  end

  after(:each) do
    @content_list.each do |content|
      # cant delete content in hosted mode as its locked
      @cp.delete_content(@owner['key'], content.id) unless is_hosted?
    end
  end

  it 'will regen the v1 entitlement cert based on content set limit' do
    # as designed, the content change will successfully cause a regeneration
    ent1 = @system.consume_product(@product1.id)[0]
    content_ids = []
    (1..10).each do |i|
      content_ids << @content_list[i].id
    end
    @cp.add_batch_content_to_product(@owner['key'], @product1.id, content_ids, true)
    @cp.regenerate_entitlement_certificates_for_product(@product1.id)
    ent2 = @system.get_entitlement(ent1.id)
    ent2.certificates[0].serial.id.should_not == ent1.certificates[0].serial.id
    revoked_serials.should include(ent1.certificates[0].serial.id)

    # the content change to > 185 will not cause a regeneration. It will also not throw an error.
    content_ids = []
    (11..200).each do |i|
      content_ids.push(@content_list[i].id)
    end
    @cp.add_batch_content_to_product(@owner['key'], @product1.id, content_ids, true)
    @cp.regenerate_entitlement_certificates_for_product(@product1.id)
    ent3 = @system.get_entitlement(ent1.id)
    ent3.certificates[0].serial.id.should == ent2.certificates[0].serial.id
    revoked_serials.should_not include(ent2.certificates[0].serial.id)

    # updating the client will allow the cert to be regenerated
    facts = {
      'system.certificate_version' => '3.2'
    }
    @system.update_consumer({:facts => facts})
    ent4 = @system.get_entitlement(ent1.id)
    ent4.certificates[0].serial.id.should_not == ent2.certificates[0].serial.id
    revoked_serials.should include(ent2.certificates[0].serial.id)
  end

  it 'will not allow an excessive content set to block others' do
    # @cp.refresh_pools(@owner['key'])

    ent1 = @system.consume_product(@product1.id)[0]
    expect(ent1).to_not be_nil

    ent2 = @system.consume_product(@product2.id)[0]
    expect(ent2).to_not be_nil

    (1..200).each do |i|
      @cp.add_content_to_product(@owner['key'], @product1.id, @content_list[i].id, true)
    end

    @cp.add_content_to_product(@owner['key'], @product2.id, @content_list[1].id, true)
    @cp.regenerate_entitlement_certificates_for_product(@product1.id)
    @cp.regenerate_entitlement_certificates_for_product(@product2.id)

    new_ent1 = @system.get_entitlement(ent1.id)
    new_ent2 = @system.get_entitlement(ent2.id)
    new_ent1.certificates[0].serial.id.should == ent1.certificates[0].serial.id
    new_ent2.certificates[0].serial.id.should_not == ent2.certificates[0].serial.id
    revoked_serials.should include(ent2.certificates[0].serial.id)
  end

  def revoked_serials
    return @cp.get_crl
  end
end
