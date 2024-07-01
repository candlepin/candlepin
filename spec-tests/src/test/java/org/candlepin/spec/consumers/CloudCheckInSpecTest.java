package org.candlepin.spec.consumers;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.junit.jupiter.api.Test;

@SpecTest
public class CloudCheckInSpecTest {

    @Test
    public void shouldCreateCloudCheckInEvent() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ConsumerDTO consumer =Consumers.random(owner);
        consumer.putFactsItem("aws_instance_id", StringUtil.random(""));
        consumer.putFactsItem("aws_account_id", StringUtil.random("aws-"));
        consumer.putFactsItem("aws_marketplace_product_codes", StringUtil.random(""));
        consumer.putFactsItem("aws_billing_products", StringUtil.random(""));

        consumer = adminClient.consumers().createConsumer(consumer);

        // Update the consumer to trigger a consumer check-in update. The request must have a
        // a consumer principal.
        ApiClient consumerClient = ApiClients.trustedConsumer(consumer.getUuid());
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);
    }

}
