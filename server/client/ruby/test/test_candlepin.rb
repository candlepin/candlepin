#! /usr/bin/env ruby
require 'net/http'
require 'webrick'
require 'webrick/https'

require 'rspec/autorun'
require_relative '../candlepin'

RSpec.configure do |config|
  config.color = true
  config.expect_with :rspec do |c|
    c.syntax = :expect
  end
end

RSpec::Matchers.define :be_2xx do
  match do |res|
    (200..206).cover?(res.status_code)
  end
end

RSpec::Matchers.define :be_unauthorized do
  match do |res|
    res.status_code == 401
  end
end

RSpec::Matchers.define :be_forbidden do
  match do |res|
    res.status_code == 403
  end
end

RSpec::Matchers.define :be_missing do
  match do |res|
    res.status_code == 404
  end
end

module Candlepin
  describe "Candlepin" do
    def rand_string(opts = {})
      len = opts[:len] || 9
      prefix = opts[:prefix] || ''
      o = [('a'..'z'), ('A'..'Z'), ('1'..'9')].map(&:to_a).flatten
      rand = (0...len).map { o[rand(o.length)] }.join

      prefix.empty? ? rand : "#{prefix}-#{rand}"
    end

    shared_context "functional context" do
      # The let! prevents lazy loading
      let!(:user_client) { BasicAuthClient.new }
      let!(:no_auth_client) { NoAuthClient.new }

      let(:owner) do
        key = rand_string(:prefix => 'owner')
        res = user_client.create_owner(
          :owner => key,
          :display_name => key,
        )
        raise "Could not create owner for test" unless res.ok?
        res.content
      end

      let(:owner_client) do
        client = BasicAuthClient.new
        client.key = owner[:key]
        client
      end

      let(:owner_user) do
        user_client.create_user_under_owner(
          :username => rand_string(:prefix => 'owner_user'),
          :password => rand_string,
          :owner => owner[:key],
          :super_admin => false,
        )
      end

      let(:role) do
        res = user_client.create_role(
          :name => rand_string(:prefix => 'role'),
        )
        raise "Could not create role for test" unless res.ok?
        res.content
      end

      let(:content) do
        res = user_client.create_owner_content(
          :content_id => "hello",
          :name => "Hello",
          :label => "hello",
          :owner => owner[:key],
        )
        raise "Could not create content for test" unless res.ok?
        res.content
      end

      let(:product) do
        p = rand_string(:prefix => 'product')
        res = user_client.create_product(
          :product_id => p,
          :name => "Product #{p}",
          :owner => owner[:key],
        )
        raise "Could not create product for test" unless res.ok?
        res.content
      end
    end

    context "in an Admin only context", :functional => true do
      include_context("functional context")

      it "lists all pools" do
        owner_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        res = user_client.get_all_pools(
          :product => product[:id],
          :owner => owner[:id],
        )
        expect(res).to be_2xx
        expect(res.content.length).to eq(1)
      end
    end

    context "in an Activation Key context", :functional => true do
      include_context("functional context")

      it 'creates activation keys' do
        owner_client = BasicAuthClient.new
        owner_client.key = owner[:key]
        res = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        )
        expect(res).to be_2xx

        activation_key = res.content

        res = user_client.get_activation_key(:id => activation_key[:id])
        expect(res).to be_2xx
      end

      it 'updates an activation key' do
        owner_client = BasicAuthClient.new
        owner_client.key = owner[:key]
        res = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        )
        expect(res).to be_2xx

        activation_key = res.content
        modified_key = activation_key.deep_dup
        modified_key.extract!(:id)
        modified_key[:description] = "Something new"

        res = user_client.update_activation_key(
          :id => activation_key[:id],
          :activation_key => modified_key)
        expect(res).to be_2xx
      end

      it 'adds pools to activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        owner_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        pools = owner_client.get_owner_pools.content

        res = owner_client.add_pool_to_activation_key(
          :id => activation_key[:id],
          :pool_id => pools.first[:id]
        )
        expect(res).to be_2xx

        res = owner_client.get_activation_key(:id => activation_key[:id])
        expect(res).to be_2xx
      end

      it 'removes pools from activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        owner_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        pools = owner_client.get_owner_pools.content

        res = owner_client.add_pool_to_activation_key(
          :id => activation_key[:id],
          :pool_id => pools.first[:id],
        )
        expect(res).to be_2xx
        expect(res.content[:pools].length).to eq(1)

        owner_client.delete_pool_from_activation_key(
          :id => activation_key[:id],
          :pool_id => pools.first[:id],
        )

        res = owner_client.get_activation_key(:id => activation_key[:id])
        expect(res).to be_2xx
        expect(res.content[:pools].length).to eq(0)
      end

      it 'adds content overrides to activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        res = owner_client.add_overrides_to_activation_key(
          :id => activation_key[:id],
          :overrides => {
            :content_label => "x",
            :name => "y",
            :value => "z",
          },
        )
        expect(res).to be_2xx

        result_key = owner_client.get_activation_key(:id => activation_key[:id]).content
        expect(result_key[:contentOverrides].length).to eq(1)
      end

      it 'removes content overrides from activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        override = {
          :content_label => "x",
          :name => "y",
          :value => "z",
        }
        res = owner_client.add_overrides_to_activation_key(
          :id => activation_key[:id],
          :overrides => override,
        )
        expect(res).to be_2xx

        result_key = owner_client.get_activation_key(:id => activation_key[:id]).content
        expect(result_key[:contentOverrides].length).to eq(1)

        owner_client.delete_overrides_from_activation_key(
          :id => activation_key[:id],
          :overrides => override,
        )
        result_key = owner_client.get_activation_key(:id => activation_key[:id]).content
        expect(result_key[:contentOverrides].length).to eq(0)
      end

      it 'adds products to activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        res = owner_client.add_product_to_activation_key(
          :id => activation_key[:id],
          :product_id => product[:id],
        )
        expect(res).to be_2xx

        res = owner_client.get_activation_key(:id => activation_key[:id])
        expect(res).to be_2xx
      end

      it 'removes products from activation keys' do
        activation_key = owner_client.create_activation_key(
          :name => rand_string,
          :description => rand_string,
        ).content

        res = owner_client.add_product_to_activation_key(
          :id => activation_key[:id],
          :product_id => product[:id],
        )
        expect(res).to be_2xx
        expect(res.content[:products].length).to eq(1)

        owner_client.delete_product_from_activation_key(
          :id => activation_key[:id],
          :product_id => product[:id],
        )

        res = owner_client.get_activation_key(:id => activation_key[:id])
        expect(res).to be_2xx
        expect(res.content[:products].length).to eq(0)
      end
    end

    context "in an Owner context", :functional => true do
      include_context("functional context")

      it 'creates owners' do
        res = user_client.create_owner(
          :owner => rand_string,
          :display_name => rand_string,
        )
        expect(res).to be_2xx
        expect(res.content).to have_key(:id)
      end

      it 'creates owner environments' do
        res = user_client.create_owner_environment(
          :owner => owner[:key],
          :id => rand_string,
          :description => rand_string,
          :name => rand_string
        )
        expect(res).to be_2xx
        expect(res.content).to have_key(:name)
      end

      it 'gets owner environments' do
        env = user_client.create_owner_environment(
          :owner => owner[:key],
          :id => rand_string,
          :description => rand_string,
          :name => rand_string
        ).content

        res = user_client.get_owner_environment(
          :owner => owner[:key],
          :name => env[:name]
        )
        expect(res).to be_2xx
      end

      it 'imports a manifest' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :type => :candlepin,
        )
        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        x509_client.bind(:pool_id => pools.first[:id])

        f = Tempfile.new(["test_export", ".zip"])
        begin
          x509_client.export_consumer_to_file(:export_file => f.path)
          expect(File.size?(f.path)).to_not eq(0)

          new_owner = user_client.create_owner(
            :owner => rand_string,
            :display_name => rand_string,
          ).content

          res = user_client.import_manifest(
            :owner => new_owner[:key],
            :manifest => f.path
          )

          expect(res).to be_2xx
        ensure
          f.close
          f.unlink
        end
      end

      it 'updates an owner' do
        old_name = owner[:displayName]
        res = owner_client.update_owner(
          :display_name => rand_string
        )
        expect(res).to be_2xx
        expect(res.content[:displayName]).to_not eq(old_name)
      end

      it 'updates an owner pool' do
        p = owner_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        original_quantity = p[:quantity]

        p[:quantity] = p[:quantity] + 10
        res = owner_client.update_pool(:pool => p)
        expect(res).to be_2xx

        modified_sub = owner_client.get_owner_subscriptions.content.first
        expect(modified_sub[:quantity]).to_not eq(original_quantity)
      end

      it 'gets owner service levels' do
        res = owner_client.get_owner_service_levels(
          :exempt => true,
        )

        expect(res).to be_2xx
      end

      it 'sets owner log level' do
        res = owner_client.set_owner_log_level(
          :level => 'debug',
        )
        expect(res).to be_2xx
        expect(res.content[:logLevel]).to eq('DEBUG')
      end

      it 'deletes owner log level' do
        res = user_client.set_owner_log_level(
          :owner => owner[:key],
          :level => 'debug',
        )
        expect(res).to be_2xx
        expect(res.content[:logLevel]).to eq('DEBUG')

        res = user_client.delete_owner_log_level(
          :owner => owner[:key],
        )
        expect(res).to be_2xx
      end

      it 'lists owner consumers by type' do
        user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        res = user_client.get_owner_consumers(
          :owner => owner[:key],
          :types => :system,
        )
        expect(res).to be_2xx
        expect(res.content.length).to eq(1)

        res = user_client.get_owner_consumers(
          :owner => owner[:key],
          :types => :candlepin,
        )
        expect(res).to be_2xx
        expect(res.content).to be_empty
      end

      it 'refreshes pools synchronously' do
        prod_id = rand_string
        user_client.create_product(
          :product_id => prod_id,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        )

        user_client.create_pool(
          :owner => owner[:key],
          :product_id => prod_id,
        )

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:multiplier]).to eq(2)

        user_client.update_product(
          :product_id => prod_id,
          :multiplier => 4,
          :owner => owner[:key],
        )

        result = user_client.refresh_pools(:owner => owner[:key])
        expect(result).to be_2xx

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:multiplier]).to eq(4)
      end

      it 'refreshes pools asynchronously' do
        prod_id = rand_string
        owner_client.create_product(
          :product_id => prod_id,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
        )

        owner_client.create_pool(
          :product_id => prod_id,
        )

        pools = owner_client.get_owner_pools.content
        expect(pools.first[:product][:multiplier]).to eq(2)

        owner_client.update_product(
          :product_id => prod_id,
          :multiplier => 4,
        )

        result = owner_client.refresh_pools_async
        expect(result).to be_kind_of(HTTPClient::Connection)
        result.join
        expect(result.pop).to be_2xx

        pools = owner_client.get_owner_pools.content
        expect(pools.first[:product][:multiplier]).to eq(4)
      end

      it 'refreshes pools for a product' do
        prod_id = rand_string
        user_client.create_product(
          :product_id => prod_id,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        )

        user_client.create_pool(
          :owner => owner[:key],
          :product_id => prod_id,
        )

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:multiplier]).to eq(2)

        user_client.update_product(
          :product_id => prod_id,
          :multiplier => 4,
          :owner => owner[:key],
        )

        result = user_client.refresh_pools_for_product(:product_ids => prod_id)
        expect(result).to be_2xx

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:multiplier]).to eq(4)
      end

      it 'gets owner hypervisors' do
        host1 = user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
        ).content

        host2 = user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
        ).content

        user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
          :hypervisor_id => host1[:uuid],
        ).content

        res = user_client.get_owner_hypervisors(
          :owner => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_hypervisors(
          :owner => owner[:key],
          :hypervisor_ids => [host1[:uuid], host2[:uuid]],
        )
        expect(res).to be_2xx
        expect(res.content.length).to eq(1)
      end

      it 'deletes owners' do
        res = owner_client.delete_owner
        expect(res).to be_2xx

        res = user_client.get_owner(
          :owner => owner[:key]
        )
        expect(res).to be_missing
      end

      it 'gets owners' do
        res = owner_client.get_owner
        expect(res).to be_2xx
        expect(res.content[:id]).to eq(owner[:id])
      end

      it 'gets owner info' do
        res = owner_client.get_owner_info
        expect(res).to be_2xx
      end

      it "gets owner jobs" do
        res = owner_client.get_owner_jobs
        expect(res).to be_2xx
      end

      it 'creates child owners' do
        parent = owner
        child = user_client.create_owner(
          :owner => rand_string,
          :display_name => rand_string,
          :parent_owner => parent,
        ).content

        expect(child[:parentOwner][:id]).to eq(parent[:id])
        expect(parent[:parentOwner]).to be_nil
      end

      it 'gets owners with pools of a product' do
        id1 = rand_string
        user_client.create_product(
          :product_id => id1,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        )

        user_client.create_pool(
          :owner => owner[:key],
          :product_id => id1,
        )

        results = user_client.get_owners_with_product(:product_ids => [id1]).content
        expect(results.first[:key]).to eq(owner[:key])
      end
    end

    context "in an Owner Product context", :functional => true do
      include_context("functional context")

      it 'creates an owner product' do
        res = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        )
        expect(res).to be_2xx
        expect(res.content[:multiplier]).to eq(2)
      end

      it 'deletes an owner product' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        ).content

        res = user_client.delete_product(
          :product_id => product[:id],
          :owner => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_product(
          :product_id => product[:id],
          :owner => owner[:key],
        )
        expect(res).to be_missing
      end

      it 'updates an owner product' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        ).content

        res = user_client.update_product(
          :product_id => product[:id],
          :multiplier => 8,
          :owner => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_product(
          :product_id => product[:id],
          :owner => owner[:key],
        )
        expect(res.content[:multiplier]).to eq(8)
      end
    end

    context "in an Owner Content context", :functional => true do
      include_context("functional context")

      it 'creates owner content' do
        res = owner_client.create_owner_content(
          :content_id => "hello",
          :name => "Hello",
          :label => "hello",
        )

        expect(res).to be_2xx

        content = owner_client.get_owner_content(
          :content_id => "hello",
        ).content

        expect(content[:label]).to eq("hello")
      end

      it 'batch creates owner content' do
        content = []
        5.times do |i|
          content << {
            :content_id => "content_#{i}",
            :name => "Content #{i}",
            :label => "content_#{i}",
            :content_url => "http://www.example.com",
          }
        end

        res = user_client.create_batch_owner_content(
          :owner => owner[:key],
          :content => content,
        )

        expect(res).to be_2xx

        content = user_client.get_owner_content(
          :content_id => "content_4",
          :owner => owner[:key],
        ).content

        expect(content[:label]).to eq("content_4")
      end

      it 'updates owner content' do
        user_client.create_owner_content(
          :content_id => "hello",
          :name => "Hello",
          :label => "hello",
          :owner => owner[:key],
          :content_url => "http://www.example.com",
        )

        res = user_client.update_owner_content(
          :content_id => "hello",
          :owner => owner[:key],
          :label => "goodbye",
        )

        expect(res).to be_2xx

        content = user_client.get_owner_content(
          :content_id => "hello",
          :owner => owner[:key],
        ).content

        expect(content[:label]).to eq("goodbye")
        expect(content[:contentUrl]).to eq("http://www.example.com")
      end
    end

    context "in an Environment context", :functional => true do
      include_context("functional context")

      it 'creates an consumer in an environment' do
        env = user_client.create_owner_environment(
          :owner => owner[:key],
          :name => rand_string,
          :description => rand_string,
          :id => rand_string
        ).content

        consumer = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        # Used the returned consumer as a dummy
        consumer.extract!(:id, :uuid, :idCert)

        res = user_client.create_consumer_in_environment(
          :consumer => consumer,
          :owner => owner[:key],
          :env_id => env[:id],
        )
        expect(res).to be_2xx
      end

      it 'promotes content to an environment' do
        env = user_client.create_owner_environment(
          :owner => owner[:key],
          :name => rand_string,
          :description => rand_string,
          :id => rand_string
        ).content

        res = user_client.promote_content(
          :env_id => env[:id],
          :content_ids => content[:id],
        )
        expect(res).to be_2xx
      end

      it 'demotes content from an environment' do
        env = user_client.create_owner_environment(
          :owner => owner[:key],
          :name => rand_string,
          :description => rand_string,
          :id => rand_string
        ).content

        res = user_client.promote_content(
          :env_id => env[:id],
          :content_ids => content[:id],
        )
        expect(res).to be_2xx

        res = user_client.demote_content(
          :env_id => env[:id],
          :content_ids => content[:id],
        )
        expect(res).to be_2xx
      end
    end

    context "in a Product Content context", :functional => true do
      include_context("functional context")

      it 'updates product content' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        ).content

        res = user_client.update_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :owner => owner[:key],
        )

        expect(res).to be_2xx
      end

      it 'deletes product content' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :owner => owner[:key],
        ).content
        expect(product[:productContent]).to be_empty

        res = user_client.update_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :owner => owner[:key],
        )
        expect(res).to be_2xx

        product = user_client.get_product(
          :product_id => product[:id],
        ).content
        expect(product[:productContent]).to_not be_empty

        res = user_client.delete_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :owner => owner[:key],
        )
        expect(res).to be_2xx

        product = user_client.get_product(
          :product_id => product[:id],
        ).content
        expect(product[:productContent]).to be_empty
      end
    end

    context "in an Role context", :functional => true do
      include_context("functional context")

      it 'creates roles' do
        res = user_client.create_role(
          :name => rand_string(:prefix => 'role'),
        )
        expect(res).to be_2xx

        expect(res.content[:id]).to_not be_nil
      end

      it 'gets roles' do
        res = user_client.get_role(
          :role_id => role[:id],
        )
        expect(res.content[:id]).to eq(role[:id])
      end

      it 'updates roles' do
        res = user_client.update_role(
          :role_id => role[:id],
          :name => rand_string,
        )
        expect(res.content[:name]).to_not eq(role[:name])
      end

      it 'deletes roles' do
        expect(role[:id]).to_not be_nil

        res = user_client.delete_role(
          :role_id => role[:id],
        )
        expect(res).to be_2xx
      end

      it 'creates role users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.add_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users].first[:id]).to eq(user[:id])
      end

      it 'deletes role users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.add_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users].first[:id]).to eq(user[:id])

        res = user_client.delete_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users]).to be_empty
      end

      it 'adds role permissions' do
        res = user_client.add_role_permission(
          :role_id => role[:id],
          :owner => owner[:key],
          :type => 'OWNER',
          :access => 'ALL',
        )
        expect(res).to be_2xx
      end

      it 'deletes role permissions' do
        perm = user_client.add_role_permission(
          :role_id => role[:id],
          :owner => owner[:key],
          :type => 'OWNER',
          :access => 'ALL',
        ).content

        res = user_client.delete_role_permission(
          :role_id => role[:id],
          :permission_id => perm[:permissions].first[:id],
        )
        expect(res).to be_2xx
      end
    end

    context "in a Register context", :functional => true do
      include_context("functional context")
      it 'registers a consumer' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        expect(res.content[:uuid].length).to eq(36)
      end

      it 'registers a consumer and gets a client' do
        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :username => owner[:key],
          :name => rand_string,
        )

        res = x509_client.get_consumer
        expect(res.content[:uuid].length).to eq(36)
      end

      it 'fails gracefully if a consumer client can not be created' do
        expect do
          user_client.register_and_get_client(
            :owner => "NO_OWNER",
            :username => owner[:key],
            :name => rand_string,
          )
        end.to raise_error(HTTPClient::BadResponseError)
      end
    end

    context "in a Consumer context", :functional => true do
      include_context("functional context")

      it 'gets compliance status' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )
        x509_client.bind(:pool_id => pools.first[:id])
        res = x509_client.get_consumer_compliance
        expect(res).to be_2xx
        expect(res.content[:status]).to eq('valid')
      end

      it 'gets a consumer export' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :type => :candlepin,
        )
        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        x509_client.bind(:pool_id => pools.first[:id])

        res = x509_client.export_consumer
        expect(res).to be_2xx
        expect(res.content_type).to eq("application/zip")
      end

      it 'writes an export to disk' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :type => :candlepin,
        )
        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        x509_client.bind(:pool_id => pools.first[:id])

        f = Tempfile.new(["test_export", ".zip"])
        begin
          x509_client.export_consumer_to_file(:export_file => f.path)
          expect(File.size?(f.path)).to_not eq(0)
        ensure
          f.close
          f.unlink
        end
      end

      it 'gets a list of entitlements' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )
        x509_client.bind(:pool_id => pools.first[:id])

        res = x509_client.get_consumer_entitlements(:product_id => product[:id])
        expect(res).to be_2xx
        expect(res.content.length).to eq(1)
      end

      it 'gets a list of compliance statuses' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )
        x509_client.bind(:pool_id => pools.first[:id])

        res = user_client.get_compliance_list(:uuids => x509_client.uuid)
        expect(res).to be_2xx
        expect(res.content[x509_client.uuid.to_sym][:status]).to eq('valid')
      end

      it 'regenerates an identity certificate' do
        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )
        res = x509_client.get_consumer_compliance
        expect(res).to be_2xx
        original_cert_serial = x509_client.client_cert.serial

        new_client = x509_client.regen_identity_certificate_and_get_client
        expect(res).to be_2xx

        expect(new_client.client_cert.serial).to_not eq(original_cert_serial)
        res = new_client.get_consumer_compliance
        expect(res).to be_2xx
      end
    end

    context "in a Bind context", :functional => true do
      include_context("functional context")

      it 'binds to a pool ID' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )

        res = x509_client.bind(:pool_id => pools.first[:id])
        expect(res).to be_2xx

        entitlement = res.content.first
        expect(entitlement[:certificates]).to_not be_empty
        expect(entitlement[:certificates].first.key?(:key)).to be_true
      end

      it 'performs a dry run of autobind' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :installed_products => product[:id],
        )

        res = x509_client.autobind_dryrun
        expect(res).to be_2xx
      end

      it 'binds to a product ID' do
        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :username => owner_user[:username],
          :installed_products => product,
        )

        res = x509_client.bind(:product => product[:id])
        expect(res).to be_2xx

        entitlement = res.content.first
        expect(entitlement[:certificates]).to_not be_empty
        expect(entitlement[:certificates].first.key?(:key)).to be_true
      end

      it 'does not allow binding by both pool and product' do
        expect do
          user_client.bind(:product => 'foo', :pool => 'bar', :uuid => 'quux')
        end.to raise_error(ArgumentError)
      end
    end

    context "in a User context", :functional => true do
      include_context("functional context")

      it 'creates users' do
        res = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        )
        expect(res).to be_2xx
        user = res.content
        expect(user[:hashedPassword].length).to eq(40)
      end

      it 'creates users under owners' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :owner => owner[:key])
        expect(user[:username]).to eq(name)
        expect(user[:password]).to eq(password)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'creates multiple users under an owner' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :owner => owner[:key])
        expect(user[:username]).to eq(name)
        expect(user[:password]).to eq(password)

        name2 = rand_string
        password2 = rand_string
        user2 = user_client.create_user_under_owner(
          :username => name2,
          :password => password2,
          :owner => owner[:key])
        expect(user2[:username]).to eq(name2)
        expect(user2[:password]).to eq(password2)

        [user[:username], user2[:username]].each do |n|
          res = user_client.get_user_roles(:username => n)
          roles = res.content.map { |r| r[:name] }
          expect(roles).to include("#{owner[:key]}-ALL")
        end
      end

      it 'resets a client to a given user' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :owner => owner[:key])

        expect(user_client.username).to eq('admin')
        user_client.switch_auth(user[:username], user[:password])

        expect(user_client.username).to eq(name)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'resets a client to a given user by passing in a user' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :owner => owner[:key])

        expect(user_client.username).to eq('admin')
        user_client.switch_auth(user)

        expect(user_client.username).to eq(name)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'gets users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.get_user(:username => user[:username])
        expect(res.content[:id]).to eq(user[:id])
      end

      it 'updates users' do
        res = user_client.update_user(
          :username => owner_user[:username],
          :password => rand_string)
        expect(res.content[:hashedPassword]).to_not eq(owner_user[:hashedPassword])
      end

      it 'deletes users' do
        res = user_client.delete_user(:username => owner_user[:username])
        expect(res).to be_2xx

        res = user_client.get_all_users
        existing_users = res.content.map { |u| u[:username] }
        expect(existing_users).to_not include(owner_user[:username])
      end
    end

    context "in a Content Override context", :functional => true do
      include_context("functional context")

      it 'adds content overrides' do
        consumer = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        overrides = []
        5.times do |i|
          overrides << {
            :content_label => "Label #{i}",
            :name => "override_#{i}",
            :value => i,
          }
        end

        res = user_client.create_content_overrides(
          :uuid => consumer[:uuid],
          :overrides => overrides,
        )
        expect(res).to be_2xx
      end

      it 'get content overrides' do
        consumer = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        res = user_client.create_content_overrides(
          :uuid => consumer[:uuid],
          :overrides => {
            :content_label => "x",
            :name => "y",
            :value => "z",
          },
        )
        expect(res).to be_2xx

        overrides = user_client.get_content_overrides(
          :uuid => consumer[:uuid]
        ).content
        expect(overrides.length).to eq(1)
        expect(overrides.first).to have_key(:contentLabel)
      end

      it 'deletes content overrides' do
        consumer = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        single_override = {
          :content_label => "x",
          :name => "y",
          :value => "z",
        }
        res = user_client.create_content_overrides(
          :uuid => consumer[:uuid],
          :overrides => single_override,
        )
        expect(res).to be_2xx

        overrides = user_client.get_content_overrides(
          :uuid => consumer[:uuid]
        ).content
        expect(overrides.length).to eq(1)
        expect(overrides.first).to have_key(:contentLabel)

        res = user_client.delete_content_overrides(
          :uuid => consumer[:uuid],
          :overrides => single_override,
        )
        expect(res).to be_2xx
      end
    end

    context "in an miscellaneous context", :functional => true do
      include_context("functional context")

      # The statistics are so slow that the HTTP request times
      # out half the time.  Not worth the trouble at the moment.
      # it 'gets owner statistics' do
      #   user_client.register(
      #     :owner => owner[:key],
      #     :username => owner_user[:username],
      #     :name => rand_string,
      #   )
      #   res = user_client.get_owner_statistics(
      #     :owner => owner[:key],
      #     :type => "SYSTEM/PHYSICAL",
      #   )
      #   expect(res).to be_2xx
      # end

      it 'gets a status as JSON' do
        res = no_auth_client.get('/status')
        expect(res.content.key?(:version)).to be_true
      end

      it 'gets all owners with basic auth' do
        user_client.create_owner(
          :owner => rand_string,
          :display_name => rand_string,
        )
        res = user_client.get_all_owners
        expect(res.content.empty?).to be_false
        expect(res.content.first.key?(:id)).to be_true
      end

      it 'fails with bad password' do
        res = no_auth_client.get('/owners')
        expect(res).to be_unauthorized
      end

      it 'gets deleted consumers' do
        res = user_client.get_deleted_consumers
        expect(res).to be_2xx
      end

      it 'updates a consumer' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content

        res = user_client.update_consumer(
          :autoheal => false,
          :uuid => consumer[:uuid],
          :capabilities => [:cores],
        )
        expect(res).to be_2xx
      end

      it 'regenerates a certificate by entitlement' do
        consumer = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )

        res = x509_client.bind(:pool_id => pools.first[:id])
        expect(res).to be_2xx

        entitlement = res.content.first

        res = user_client.regen_certificates_by_consumer(
          :uuid => consumer[:uuid],
          :entitlement_id => entitlement[:id],
          :lazy_regen => false)

        expect(res).to be_2xx

        regened_entitlement = x509_client.get_entitlement(
          :entitlement_id => entitlement[:id]).content

        new_cert = regened_entitlement[:certificates].first[:cert]
        old_cert = entitlement[:certificates].first[:cert]
        expect(new_cert).to_not eq(old_cert)
      end

      it 'regenerates a certificate by product' do
        user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        ).content

        user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )

        pools = user_client.get_owner_pools(:owner => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )

        res = x509_client.bind(:pool_id => pools.first[:id])
        expect(res).to be_2xx

        res = user_client.regen_certificates_by_product(
          :product_id => product[:id],
          :lazy_regen => false)

        expect(res).to be_2xx
        # Regenerating by product begins a job
        expect(res.content).to have_key(:state)
      end

      it 'allows a client to set a sticky uuid' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        res = user_client.update_consumer(
          :autoheal => false,
        )
        expect(res).to be_2xx
      end

      it 'allows a client to set a sticky owner key' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.key = consumer[:owner][:key]

        res = user_client.get_owner_info
        expect(res).to be_2xx
      end

      it 'updates a consumer guest id list' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        res = user_client.update_all_guest_ids(
          :guest_ids => ['123', '456'],
        )
        expect(res).to be_2xx
      end

      it 'deletes a guest id' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        user_client.update_consumer(
          :guest_ids => ['x', 'y', 'z'],
        )
        expect(res).to be_2xx

        res = user_client.delete_guest_id(
          :guest_id => 'x',
        )
        expect(res).to be_2xx
      end

      it 'gets a crl' do
        crl = user_client.get_crl
        expect(crl).to be_kind_of(OpenSSL::X509::CRL)
      end

      it 'gets environments' do
        res = user_client.get_environments
        expect(res).to be_2xx
      end

      it 'creates subscriptions' do
        res = user_client.create_pool(
          :owner => owner[:key],
          :product_id => product[:id],
        )
        expect(res).to be_2xx
        expect(res.content[:product][:id]).to eq(product[:id])
      end

      it 'creates a distributor version' do
        name = rand_string
        res = user_client.create_distributor_version(
          :name => name,
          :display_name => rand_string,
          :capabilities => [:ram],
        )
        expect(res).to be_2xx
        expect(res.content[:name]).to eq(name)
      end

      it 'deletes a distributor version' do
        distributor = user_client.create_distributor_version(
          :name => rand_string,
          :display_name => rand_string,
        ).content

        res = user_client.delete_distributor_version(
          :id => distributor[:id]
        )
        expect(res).to be_2xx
      end

      it 'updates a distributor version' do
        distributor = user_client.create_distributor_version(
          :name => rand_string,
          :display_name => rand_string,
          :capabilities => [:ram],
        ).content

        new_display_name = rand_string
        res = user_client.update_distributor_version(
          :id => distributor[:id],
          :display_name => new_display_name,
        )
        expect(res).to be_2xx

        res = user_client.get_distributor_version(
          :name => distributor[:name]
        )
        expect(res.content.first[:displayName]).to eq(new_display_name)
      end

      it 'creates a consumer type' do
        res = user_client.create_consumer_type(
          :label => rand_string
        )
        expect(res).to be_2xx
      end

      it 'deletes a consumer type' do
        type = user_client.create_consumer_type(
          :label => rand_string
        ).content

        res = user_client.delete_consumer_type(
          :type_id => type[:id]
        )
        expect(res).to be_2xx

        res = user_client.get_consumer_type(
          :type_id => type[:id]
        )
        expect(res).to be_missing
      end
    end

    context "in a unit test context", :unit => true do
      TEST_PORT = 11999
      CLIENT_CERT_TEST_PORT = TEST_PORT + 1
      attr_accessor :server
      attr_accessor :client_cert_server

      before(:all) do
        util_test_class = Class.new(Object) do
          include Util
        end
        Candlepin.const_set("UtilTest", util_test_class)
      end

      before(:each) do
        key = OpenSSL::PKey::RSA.new(File.read('certs/server.key'))
        cert = OpenSSL::X509::Certificate.new(File.read('certs/server.crt'))

        server_config = {
          :BindAddress => 'localhost',
          :Port => TEST_PORT,
          :SSLEnable => true,
          :SSLPrivateKey => key,
          :SSLCertificate => cert,
          :Logger => WEBrick::BasicLog.new(nil, WEBrick::BasicLog::FATAL),
          :AccessLog => [],
        }

        @server = WEBrick::HTTPServer.new(server_config)
        @client_cert_server = WEBrick::HTTPServer.new(
          server_config.merge(
            :SSLVerifyClient =>
              OpenSSL::SSL::VERIFY_PEER | OpenSSL::SSL::VERIFY_FAIL_IF_NO_PEER_CERT,
            :SSLCACertificateFile => 'certs/test-ca.crt',
            :Port => CLIENT_CERT_TEST_PORT,
          )
        )

        [server, client_cert_server].each do |s|
          s.mount_proc('/candlepin/status') do |req, res|
            if req.accept.include?('text/plain')
              res.body = 'Hello Text'
              res['Content-Type'] = 'text/plain'
            elsif req.accept.include?('bad/type')
              res.body = 'ERROR'
              res['Content-Type'] = 'text/plain'
            else
              res.body = '{ "message": "Hello" }'
              res['Content-Type'] = 'text/json'
            end
          end
        end

        @server_thread = Thread.new do
          server.start
        end

        @client_cert_server_thread = Thread.new do
          client_cert_server.start
        end
      end

      after(:each) do
        server.shutdown unless server.nil?
        @server_thread.join unless @server_thread.nil?

        client_cert_server.shutdown unless client_cert_server.nil?
        @client_cert_server_thread.join unless @client_cert_server_thread.nil?
      end

      it 'uses CA if given' do
        simple_client = NoAuthClient.new(
          :ca_path => 'certs/test-ca.crt',
          :port => TEST_PORT,
          :insecure => false)

        res = simple_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'makes text/plain requests' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT)
        res = simple_client.get_text('/status')
        expect(res.content).to eq("Hello Text")
      end

      it 'uses an symbol access hashes' do
        simple_client = NoAuthClient.new(
          :ca_path => 'certs/test-ca.crt',
          :port => TEST_PORT,
          :insecure => false)

        res = simple_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'allows arbitrary accept headers' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT)
        res = simple_client.get_type('bad/type', '/status')
        expect(res.content).to eq("ERROR")
      end

      it 'fails fast if told to do so' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT,
          :fail_fast => true)
        expect do
          simple_client.get('/does/not/exist')
        end.to raise_error(HTTPClient::BadResponseError)
      end

      it 'fails fast if set after the fact' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT)
        res = simple_client.get('/does/not/exist')
        expect(res.status).to eq(404)

        simple_client.fail_fast = true
        expect do
          res = simple_client.get('/does/not/exist')
        end.to raise_error(HTTPClient::BadResponseError)
      end

      it 'fails to connect if no CA given in strict mode' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT,
          :insecure => false)

        expect do
          simple_client.get('/status')
        end.to raise_error(OpenSSL::SSL::SSLError)
      end

      it 'allows a connection with a valid client cert' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/client.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/client.key'))
        cert_client = X509Client.new(
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false,
          :client_cert => client_cert,
          :client_key => client_key)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'forbids a connection with an invalid client cert' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        cert_client = X509Client.new(
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false,
          :client_cert => client_cert,
          :client_key => client_key)

        expect do
          cert_client.get('/status')
        end.to raise_error(OpenSSL::SSL::SSLError, /unknown ca/)
      end

      it 'builds a correct base url' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "/some_path/",
        )
        expect(simple_client.base_url).to eq("https://www.example.com:8443/some_path/")
      end

      it 'handles a context with no leading slash' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "no_slash_path",
        )
        expect(simple_client.base_url).to eq("https://www.example.com:8443/no_slash_path/")
      end

      it 'reloads underlying client when necessary' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "/1",
        )
        url1 = "https://www.example.com:8443/1/"
        expect(simple_client.base_url).to eq(url1)
        expect(simple_client.raw_client.base_url).to eq(url1)
        expect(simple_client.raw_client).to be_kind_of(HTTPClient)

        simple_client.context = "/2"
        simple_client.reload

        url2 = "https://www.example.com:8443/2/"
        expect(simple_client.base_url).to eq(url2)
        expect(simple_client.raw_client.base_url).to eq(url2)
      end

      it 'builds a client from consumer json' do
        # Note that the consumer.json file has had the signed client.crt and
        # client.key contents inserted into it.
        cert_client = X509Client.from_consumer(
          JSON.load(File.read('json/consumer.json')),
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'fails to build client when given both consumer and cert info' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        expect do
          X509Client.from_consumer(
            JSON.load(File.read('json/consumer.json')),
            :port => CLIENT_CERT_TEST_PORT,
            :ca_path => 'certs/test-ca.crt',
            :client_cert => client_cert,
            :client_key => client_key,
            :insecure => false)
        end.to raise_error(ArgumentError)
      end

      it 'builds a client from cert and key files' do
        cert_client = X509Client.from_files(
          'certs/client.crt',
          'certs/client.key',
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'fails to build client when given both cert objects and cert files' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        expect do
          X509Client.from_files(
            'certs/client.crt',
            'certs/client.key',
            :port => CLIENT_CERT_TEST_PORT,
            :ca_path => 'certs/test-ca.crt',
            :client_cert => client_cert,
            :client_key => client_key,
            :insecure => false)
        end.to raise_error(ArgumentError)
      end

      it 'can select a subset of a hash' do
        original = {
          :x => 1,
          :y => nil,
          :z => 3,
        }
        expected_keys = [:x, :y]
        selected = UtilTest.new.select_from(original, :x, :y)
        expect(selected.keys).to match_array(expected_keys)
      end

      it 'raises an error if not a proper subset' do
        original = {
          :x => 1,
        }
        expect do
          UtilTest.new.select_from(original, :x, :y)
        end.to raise_error(ArgumentError, /Missing keys.*:y/)
      end

      it 'raises an exception on invalid option keys' do
        hash = {
          :good => 'Clint Eastwood',
          :bad => 'Lee Van Cleef',
          :weird => 'Steve Buscemi',
        }
        defaults = { :good => '', :bad => '' }
        msg_regex = /Unknown key: :weird/

        expect do
          UtilTest.new.verify_and_merge(hash, defaults)
        end.to raise_error(ArgumentError, msg_regex)
      end

      it 'verifies valid keys' do
        hash = {
          :good => 'Clint Eastwood',
          :bad => 'Lee Van Cleef',
        }
        defaults = { :good => '', :bad => '' }

        expect do
          UtilTest.new.verify_and_merge(hash, defaults)
        end.not_to raise_error
      end

      it 'turns snake case symbols into camel case symbols' do
        snake = :hello_world
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq(:helloWorld)

        snake = :hello
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq(:hello)
      end

      it 'turns snake case strings into camel case strings' do
        snake = "hello_world"
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq("helloWorld")

        snake = "hello"
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq("hello")
      end

      it 'converts hash keys into camel case' do
        h = {
          :hello_world => 'x',
          :y => 'z',
        }
        camel_hash = UtilTest.new.camelize_hash(h)
        expect(camel_hash.keys.sort).to eq([:helloWorld, :y])
      end

      it 'converts hash subsets into camel case' do
        h = {
          :hello_world => 'x',
          :y => 'z',
        }
        camel_hash = UtilTest.new.camelize_hash(h, :hello_world)
        expect(camel_hash.keys.sort).to eq([:helloWorld])
      end

      it 'validation fails for nil keys' do
        h = {
          :x => nil,
          :y => nil,
          :z => true,
        }
        expect do
          UtilTest.new.validate_keys(h)
        end.to raise_error
      end

      it 'validates specific keys' do
        h = {
          :x => nil,
          :y => nil,
          :z => true,
        }
        expect do
          UtilTest.new.validate_keys(h, :z)
        end.to_not raise_error
      end

      it 'validates keys are not nil' do
        h = {
          :z => true,
        }

        expect do
          UtilTest.new.validate_keys(h)
        end.not_to raise_error
      end

      it 'validates keys using a provided block' do
        h = {
          :z => 1,
        }

        expect do
          UtilTest.new.validate_keys(h) do |k|
            k > 5
          end
        end.to raise_error
      end
    end
  end
end
