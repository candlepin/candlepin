When /^I unbind my "([^\"]*)" Entitlement$/ do |product_id|
    @consumed = @candlepin.list_entitlements(product_id)[0]['entitlement']['pool']['consumed']
    @candlepin.list_entitlements(product_id).collect do |entitlement|
        @candlepin.unbind_entitlement(entitlement['entitlement']['id'])
    end
end

Then /^my "([^\"]*)" entitlement is returned to the pool$/ do |product_id|
    unbound_consumed = @candlepin.get_pools(:product=>product_id,
                                            :consumer=>@candlepin.consumer['uuid'])[0]['pool']['consumed']
    unbound_consumed.should == @consumed - 1
end
