require 'candlepin_api'
require 'pp'

# Provides initialization and cleanup of data that was used in the scenario
shared_examples_for 'Candlepin Scenarios' do

  before do
    @cp = Candlepin.new('admin', 'admin')
    @owners = []
    @products = []
  end

  after do
    @owners.each { |owner| @cp.delete_owner owner.id }

    # TODO:  delete products
  end
end

module CandlepinMethods

  def create_owner(owner_name)
    owner = @cp.create_owner(owner_name)
    @owners << owner

    return owner
  end

  def create_product(product_name, *args)
    @cp.create_product(product_name, *args)
  end

  def user_client(owner, user_name)
    @cp.create_user(owner.id, user_name, 'password')
    Candlepin.new(user_name, 'password')
  end

  def consumer_client(cp_client, consumer_name, type=:system)
    consumer = cp_client.register(consumer_name, type)
    Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert.key)
  end

  def random_string(prefix)
    "%s-%s" % [prefix, rand(100000)]
  end

end

# This allows for dot notation instead of using hashes for everything
class Hash

  # Not sure if this is a great idea
  # Override Ruby's id method to access our id attribute
  def id
    self['id']
  end

  def method_missing(method, *args)
    self[method.to_s]
  end
end
