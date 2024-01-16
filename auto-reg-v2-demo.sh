#!/bin/bash

# Requirements:
# 1. docker installed
# 2. docker-compose installed
# 3. run the script using sudo
# 4. In ./dev-container/docker-compose.yml set CANDLEPIN_AUTH_CLOUD_ENABLE to "true"

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
will be associated to 'product-1'. \n"

read -p "press key to continue"

echo -e "curl -k -H "Content-Type: application/json" -d '{"cloudOfferId": "offer-1", "productIds": ["product-1"]}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/offers\n"
curl -k -H "Content-Type: application/json" -d '{"cloudOfferId": "offer-1", "productIds": ["product-1"]}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/offers
sleep 1

echo -e "\nWe will start with no cloud account or organization information in the Candlepin adapters. This is to simulate someone 
creating cloud VMs without a Red Hat account. The following request is an authentication request providing cloud instance metadata.
We will provide a cloud account (cloud-account-1) and our cloud offering ID (offer-1). \n"

read -p "press key to continue"

echo -e "curl -k -H "Content-Type: application/json" -d '{"type":"test-type", "signature":"signature", "metadata":"{\"accountId\":\"account-1\", \"instanceId\":\"instance-1\", \"cloudOfferingId\": \"offer-1\"}"}' -X POST -u admin:admin https://localhost:8443/candlepin/cloud/authorize?version=2\n"
curl -k -H "Content-Type: application/json" -d '{"type":"test-type", "signature":"signature", "metadata":"{\"accountId\":\"account-1\", \"instanceId\":\"instance-1\", \"cloudOfferingId\": \"offer-1\"}"}' -X POST -u admin:admin https://localhost:8443/candlepin/cloud/authorize?version=2
sleep 1

echo -e "\nWe receive an anonymous bearer token because Candlepin does not yet have an anonymous organization to register the system to. This bearer token can then be used to get an anonymous content access certificate for content access now and to attempt to register when the anonymous org is created. 
We can now attempt to register using the anonymous token while the anonymous organization does not exist. \n"

read -p "press key to continue"

echo -e "\nInsert token\n"

read token

echo -e "curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true\n"
curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true

sleep 1

echo -e "\nAn exception is returned to the client with a retry duration. The purpose of this retry duration is to give some more time for Candlepin and the rest of the 
system to create the anonymous organization. The client will continue to try to register until the anonymous organization is created. Now we can simulate that the anonymous organization
has been created and entitled properly. The following commands will create the anonymous organization, create the product-1, and entitle the owner with product-1 in both Candlepin and upstream of Candlepin. \n"

read -p "press key to continue"

### Initializing the adapters ###

echo -e "\ncreating product in the adapters...\n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/products\n"
curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/products

sleep 1

echo -e "\ncreating the anonymous org in the adapters... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"displayName":"account-1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/owners\n"
curl -k -H "Content-Type: application/json" -d '{"displayName":"account-1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/owners

sleep 1

echo -e "\ncreating the cloud account in the adapters... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"cloudAccountId":"account-1","ownerId":"account-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/accounts\n"
curl -k -H "Content-Type: application/json" -d '{"cloudAccountId":"account-1","ownerId":"account-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/cloud/accounts

sleep 1

echo -e "\ncreating the subscription in the adapters... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"id":"test_sub","owner":{"key":"account-1","displayName":"Test account-1","contentAccessMode":"entitlement"},"product":{"id":"product-1","name":"product-1"},"quantity":10,"startDate":"2024-01-16T16:56:24.844808794Z","endDate":"2025-01-16T16:56:24.844808794Z"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/subscriptions\n"
curl -k -H "Content-Type: application/json" -d '{"id":"test_sub","owner":{"key":"account-1","displayName":"Test account-1","contentAccessMode":"entitlement"},"product":{"id":"product-1","name":"product-1"},"quantity":10,"startDate":"2024-01-16T16:56:24.844808794Z","endDate":"2025-01-16T16:56:24.844808794Z"}' -X POST -u admin:admin https://localhost:8443/candlepin/hostedtest/subscriptions

sleep 1

### Initializing Candlepin ###

echo -e "\ncreating owner in Candlepin... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"displayName":"Test account 1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment", "anonymous":"true"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners\n"
curl -k -H "Content-Type: application/json" -d '{"displayName":"Test account 1","key":"account-1","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment", "anonymous":"true"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners

sleep 1

echo -e "\ncreating product in Candlepin... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/products\n"
curl -k -H "Content-Type: application/json" -d '{"id":"product-1","name":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/products

sleep 1

echo -e "\ncreating pool for product in Candlepin... \n"

echo -e "curl -k -H "Content-Type: application/json" -d '{"quantity":10,"startDate":"2024-01-16T16:56:25.310733792Z","endDate":"2034-01-20T16:56:25.310759832Z","productId":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/pools\n"
curl -k -H "Content-Type: application/json" -d '{"quantity":10,"startDate":"2024-01-16T16:56:25.310733792Z","endDate":"2034-01-20T16:56:25.310759832Z","productId":"product-1"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners/account-1/pools

sleep 1

echo -e "\ncompleted! \n"

echo -e "\nNow that the anonymous organization has been created and entitled in Candlepin and upstream of Candlepin. The client can now register to the anonymous organization using the bearer token. \n"

read -p "press key to continue"

echo -e "curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true\n"
curl -k -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"name":"consumer-1","releaseVer":{"releaseVer":"version-1"},"owner":{"key":"account-1","displayName":"test account 1","contentAccessMode":"entitlement"},"facts":{"system.certificate_version":"3.3"},"type":{"label":"system"}}
' -X POST -u admin:admin https://localhost:8443/candlepin/consumers?owner=account-1&identity_cert_creation=true

sleep 3

echo -e "\nWe successfully register to the anonymous organization and get a content access certificate! This concludes the fast content access portion of the automatic registration v2 functionality. \n"

######### Starting the claim process #########

echo -e "\nThe customer eventually can create a Red Hat account and link this newly created Red Hat account to their cloud provider account. When they do this, we initiate a claiming process. 
This claiming process migrates systems that were registered in the anonymous organization to the claimant organization. First lets create the claimant organization (claimant-org) in Candlepin. \n"

read -p "press key to continue"

echo -e "curl -k -H "Content-Type: application/json" -d '{"displayName":"claimant org","key":"claimant-org","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners\n"
curl -k -H "Content-Type: application/json" -d '{"displayName":"claimant org","key":"claimant-org","contentAccessMode":"entitlement","contentAccessModeList":"entitlement,org_environment"}' -X POST -u admin:admin https://localhost:8443/candlepin/owners

sleep 1

echo -e "\nNow that the claimant organization is created, we can claim the anonymous organization and migrate the systems using Candlepin's new claim endpoint. \n"

read -p "press key to continue"

echo -e "curl -k -H "Content-Type: application/json" -d '{"claimant_owner_key":"claimant-org"}' -X PUT -u admin:admin https://localhost:8443/candlepin/owners/account-1/claim"
curl -k -H "Content-Type: application/json" -d '{"claimant_owner_key":"claimant-org"}' -X PUT -u admin:admin https://localhost:8443/candlepin/owners/account-1/claim

sleep 1

echo -e "\nThis claim endpoint moves the anonymous organization into a claimed state and will kick off an asynchronous job to migrate the consumers. Let's wait 5 seconds for the job to complete. \n"

sleep 5

echo -e "\nNow lets see if the systems have been migrated to the claimant organization. \n"

read -p "press key to continue"

echo -e "curl -k -u admin:admin https://localhost:8443/candlepin/owners/claimant-org/consumers"
curl -k -u admin:admin https://localhost:8443/candlepin/owners/claimant-org/consumers

sleep 1

echo -e "\nWe can now see our system under the claimant organization! This concludes the Candlepin's claiming functionality for automatic registration v2. \n"

echo -e "\nThanks for watching and let me know if there are any questions! \n"
