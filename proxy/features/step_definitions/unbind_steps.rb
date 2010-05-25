When /^I unbind my "([^\"]*)" Entitlement$/ do |product_id|
    @consumed = @consumer_cp.list_entitlements(product_id)[0]['pool']['consumed']
    @consumer_cp.list_entitlements(product_id).collect do |entitlement|
        @consumer_cp.unbind_entitlement(entitlement['id'])
    end
end

Then /^my "([^\"]*)" entitlement is returned to the pool$/ do |product_id|
    unbound_consumed = @consumer_cp.get_pools(:product=>product_id,
                                            :consumer=>@consumer_cp.uuid)[0]['consumed']
    unbound_consumed.should == @consumed - 1
end
