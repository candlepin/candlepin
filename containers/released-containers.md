# Released Containers

## Using the Development Container

The following topic includes information on how to use the Candlepin development container for testing.

### Importing Test Data

The Candlepin development container does not start with initializing the database with predefined test data, so additional steps will need to be taken after the Candlepin and database containers are running to create test data. Currently there are two options for creating test data:

1. using the Candlepin API
2. using `test_data_importer.py`

#### Using the Candlepin API

An option of generating test data is to use the Candlepin API directly using curl or using some automated process to send HTTP requests to the API. Information on the Candlepin API can be found in the [Swagger API page.](https://www.candlepinproject.org/docs/candlepin/swaggerapi.html)

Below is an example on how to create an owner that has a product and content which is a common setup for testing Candlepin. The intention is to help demonstrate how you can use the Candlepin API to setup data that can be used for testing.

1. Create owner

In this step we create an owner with the key `test-owner` that is using `org_environment` content access mode.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"displayName":"test owner", "key":"test-owner", "contentAccessMode": "org_environment", "contentAccessModeList": "entitlement,org_environment"}'
```

2. Create content

Using the owner's key from the previous step, we can create content for our owner.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners/test-owner/content \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"id":"5001", "type":"yum", "label":"test-label", "name":"test content", "vendor":"vendor", "contentUrl":"/test/path", "arches":"x86_64"}'
```

3. Create product

Now that we have content created for our owner, we can now create a product. Note that we are populating the `productContent` field using our content information from the previous step. This is to create an association between the product and the content.

```bash
curl -k -X POST https://localhost:8443/candlepin/owners/test-owner/products \
    -u admin:admin \
    -H 'Content-Type: application/json' \
    -d '{"id":"test-prod", "name":"test product", "productContent":[{"content":{"id":"5001", "type":"yum", "name":"test content", "contentUrl":"/test/path", "arches":"x86_64"}}], "href":"/products/123456"}'
```

#### Using test_data_importer.py

The `test_data_importer.py` script located in the `/bin/deployment` directory reads in predefined data from a json file and makes HTTP requests to the Candlepin API to generate the data.

An example on how to run the script once the Candlepin development container and database container are running:

```bash
python3 ./bin/deployment/test_data_importer.py ./bin/deployment/test_data.json
```
