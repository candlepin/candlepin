When /^I unregister$/ do 
  @consumed = 0
    @candlepin.get_pools().each { |pool| @consumed += pool['pool']['consumed'] }
    @candlepin.unregister()
end

Then /^all my entitlements are unbound$/ do
  @postconsumed=0
  @candlepin.get_pools().each { |pool| @postconsumed += pool['pool']['consumed'] }
  expected = @consumed - 2
  @postconsumed.should == expected

end
