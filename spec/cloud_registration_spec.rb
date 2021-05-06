require 'date'
require 'spec_helper'
require 'candlepin_scenarios'



describe 'Cloud Registration' do

    include CandlepinMethods

    before(:each) do
        skip("candlepin running in standalone mode") unless is_hosted?
        skip("cloud registration is not enabled") unless @cp.is_capability_present("cloud_registration")
    end

    def generate_token(org)
        create_upstream_owner(org)

        # Test note: At the time of writing, the testing cloud registration adapter does not care
        # at all about the type or signature, and assumes the metadata is the owner key in question.
        token = @cp.get_cloud_registration_token({
            :type => "test_type",
            :metadata => org,
            :signature => "test_signature"
        })

        return token
    end

    it "generates a valid token with valid metadata" do
        token = generate_token("test_org")
        expect(token).to_not be_nil
    end

    it "allows registration with a valid token" do
        owner = create_owner("test_org")
        token = generate_token(owner['key'])

        consumer = @cp.register("cloud_consumer", :system, nil, {}, nil, owner['key'], [], [], nil, [],
            nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil, nil, nil, nil, token)

        expect(consumer).to_not be_nil
    end

    it "fails with a 400 if no cloud registration details are provided" do
        expect {
            @cp.get_cloud_registration_token(nil)
        }.to raise_exception(RestClient::BadRequest)
    end
end
