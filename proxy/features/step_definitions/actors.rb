require 'spec/expectations'

Before do
  @owners = {}
  @user_clients = {}
  @consumer_clients = {}
  @password = 'password'   # This will be the common password for all users
end

After do
  @owners.values.each do |owner|
    @candlepin.delete_owner owner['key']
  end
end

Given /^owner "([^\"]*)" exists$/ do |owner_name|
  @owners[owner_name] = @candlepin.create_owner(owner_name)
end

Given /^user "([^\"]*)" exists under owner "([^\"]*)"$/ do |user_name, owner_name|
  owner_key = @owners[owner_name]['key']
  @candlepin.create_user(owner_key, user_name, @password)
  @user_clients[user_name] = connect(user_name, @password)
end

When /^user "([^\"]*)" registers consumer "([^\"]*)" with type "([^\"]*)"$/ do |user_name, consumer_name, consumer_type|
  consumer = @user_clients[user_name].register(consumer_name, consumer_type)
  @consumer_clients[consumer_name] = connect(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
end
