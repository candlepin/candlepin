#!/bin/bash

# Requirements:
# 1. docker installed
# 2. docker-compose installed
# 3. run the script using sudo

echo -e "Demo description: This demo will be walking through the automatic registration version 2 functionality for Candlepin. 
This demo includes the content access portion of the automatic registration version 2 functionality and the cloud account 
claiming process. This script is intended to be placed in the root directory of the candlepin project (https://github.com/candlepin/candlepin) 
to properly setup the environment and run the demo. \n"

read -p "press key to continue"

echo -e "\ncreating environment... \n"

docker compose -f ./dev-container/docker-compose.yml down

docker compose -f ./dev-container/docker-compose.yml up -d --wait

echo -e "\nenvironment created!"

echo -e "\nWe will start by setting up our cloud offering in the Candlepin adapters. We will create the cloud offering 'offering-1' which 
will be associated to 'product-1'.\n"

read -p "press key to continue"

curl -k -H "Content-Type: application/json" -d '{"cloudOfferId": "offer-1", "productIds": ["product-1"]}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/offers

echo -e "\nWe will start with no cloud account or organization information in the Candlepin adapters. This is to simulate someone 
creating cloud VMs without a Red Hat account. The following request is an authentication request providing cloud instance metadata.
We will provide a cloud account (cloud-account-1) and our cloud offering ID (offer-1).\n"

read -p "press key to continue"

curl -k -H "Content-Type: application/json" -d '{"type":"test-type", "signature":"signature", "metadata":"{\"accountId\":\"account-1\", \"instanceId\":\"instance-1\", \"cloudOfferingId\": \"offer-1\"}"}' -X POST -u admin:admin https://localhost:8443/candlepin/cloud/authorize?version=2

echo -e "\nWe recieve an anonymous bearer token for the anonymous bearer token because Candlepin does not yet have an anonymous orgization to register the 
system to. This bearer token can then be used to get an anonymous content access certificate for content access now and to attempt to register when the anonymous org is created. 
We can now attempt to register using the anonymous token.\n"

read -p "press key to continue"

echo -e "\nInsert token\n"

read token

curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true

echo -e "\nAn exception is returned to the client with a retry duration. The purpose of this is to give some more time for Candlepin and the rest of the 
system to create the anonymous organization. The client will continue to try to register until this is done. Now we can simulate that the anonymous organization
has been created and entitled properly. The following commands will create the anonymous owner, create the product-1, and entitle the owner with product-1.\n"

read -p "press key to continue"

### Initializing the adapters ###

echo -e "\ncreating product in the adapters...\n"

curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/products

echo -e "\ncreating the anonymous org in the adapters...\n"

curl -k -H "Content-Type: application/json" -d '{"displayName":"account-1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/owners

echo -e "\ncreating the cloud account in the adapters...\n"

curl -k -H "Content-Type: application/json" -d '{"cloudAccountId":"account-1","ownerId":"account-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/accounts

echo -e "\ncreating the subscription in the adapters...\n"

curl -k -H "Content-Type: application/json" -d '{"id":"test_sub","owner":{"key":"account-1","displayName":"Test account-1","contentAccessMode":"entitlement"},"product":{"id":"product-1","name":"product-1"},"quantity":10,"startDate":"2024-01-16T16:56:24.844808794Z","endDate":"2025-01-16T16:56:24.844808794Z"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/subscriptions

### Initializing Candlepin ###

echo -e "\ncreating owner in Candlepin...\n"

curl -k -H "Content-Type: application/json" -d '{"displayName":"Test account 1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners

echo -e "\ncreating product in Candlepin...\n"

curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/products

echo -e "\ncreating pool for product in Candlepin...\n"

curl -k -H "Content-Type: application/json" -d '{"quantity":10,"startDate":"2024-01-16T16:56:25.310733792Z","endDate":"2034-01-20T16:56:25.310759832Z","productId":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/pools

echo -e "\ncompleted!\n"

echo -e "\nNow that the anonymous owner has been created and entitled in Candlepin. The client can now register to the anonymous owner using the bearer token.\n"

read -p "press key to continue"

curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true

echo echo -e "\nWe successfully register to the anonymous organization and get a content access certificate! This concludes the fast content access portion of the automatic registration v2
functionality."

