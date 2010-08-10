When /^I unbind my "([^\"]*)" entitlement$/ do |product_id|
    @consumed = @consumer_cp.list_entitlements(product_id.hash.abs)[0]['pool']['consumed']
    @consumer_cp.list_entitlements(product_id.hash.abs).collect do |entitlement|
        @consumer_cp.unbind_entitlement(entitlement['id'])
    end
end

Then /^my "([^\"]*)" entitlement is returned to the pool$/ do |product_id|
    unbound_consumed = @consumer_cp.list_pools(:product=>product_id.hash.abs,
                                            :consumer=>@consumer_cp.uuid)[0]['consumed']
    unbound_consumed.should == @consumed - 1
end
