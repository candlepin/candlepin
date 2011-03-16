require 'candlepin_scenarios'

describe 'Person Consumer' do
  include CandlepinMethods

  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @monitoring = create_product()
    @cp.create_subscription(@owner.key, @monitoring.id, 4)
    @cp.refresh_pools @owner.key
  end

  it 'can be created by a user' do
    user_name = random_string("test-user")
    @user = user_client(@owner, user_name)
    consumer = @user.register("user-client", :person, nil, {}, nil)
    consumer.name.should == user_name
  end

  it 'two can not be created by the same user' do
    user_name = random_string("test-user")
    @user = user_client(@owner, user_name)
    @user.register("user-client", :person, nil, {}, nil)

    begin
      @user.register("user-client2", :person, nil, {}, nil)
    rescue RestClient::Exception => e
        e.http_code.should == 400
    else
        assert(fail, "Excepted exception was not raised")
    end

  end

end

