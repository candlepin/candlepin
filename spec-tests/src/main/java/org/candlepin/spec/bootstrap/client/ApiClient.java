/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.spec.bootstrap.client;

import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.ActivationKeyApi;
import org.candlepin.resource.client.v1.AdminApi;
import org.candlepin.resource.client.v1.CdnApi;
import org.candlepin.resource.client.v1.CertificateRevocationListApi;
import org.candlepin.resource.client.v1.CertificateSerialApi;
import org.candlepin.resource.client.v1.ConsumerTypeApi;
import org.candlepin.resource.client.v1.ContentApi;
import org.candlepin.resource.client.v1.DeletedConsumerApi;
import org.candlepin.resource.client.v1.DistributorVersionsApi;
import org.candlepin.resource.client.v1.GuestIdsApi;
import org.candlepin.resource.client.v1.HypervisorsApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.resource.client.v1.RolesApi;
import org.candlepin.resource.client.v1.RootApi;
import org.candlepin.resource.client.v1.StatusApi;
import org.candlepin.resource.client.v1.SubscriptionApi;
import org.candlepin.resource.client.v1.UsersApi;
import org.candlepin.spec.bootstrap.client.api.CloudRegistrationClient;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.EntitlementClient;
import org.candlepin.spec.bootstrap.client.api.EnvironmentClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.api.PoolsClient;
import org.candlepin.spec.bootstrap.client.api.RulesClient;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;


/**
 * An entrypoint of Candlepin client. Serves as a crossroads between specific APIs.
 */
public class ApiClient {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private final org.candlepin.invoker.client.ApiClient client;

    public ApiClient(org.candlepin.invoker.client.ApiClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public org.candlepin.invoker.client.ApiClient getApiClient() {
        return this.client;
    }

    public ActivationKeyApi activationKeys() {
        return new ActivationKeyApi(this.client);
    }

    public AdminApi admins() {
        return new AdminApi(this.client);
    }

    public CdnApi cdns() {
        return new CdnApi(this.client);
    }

    public CertificateSerialApi certificateSerial() {
        return new CertificateSerialApi(this.client);
    }

    public CloudRegistrationClient cloudAuthorization() {
        return new CloudRegistrationClient(this.client, MAPPER);
    }

    public ConsumerClient consumers() {
        return new ConsumerClient(this.client, MAPPER);
    }

    public ConsumerTypeApi consumerTypes() {
        return new ConsumerTypeApi(this.client);
    }

    public ContentApi content() {
        return new ContentApi(this.client);
    }

    public CertificateRevocationListApi crl() {
        return new CertificateRevocationListApi(this.client);
    }

    public DeletedConsumerApi deletedConsumers() {
        return new DeletedConsumerApi(this.client);
    }

    public DistributorVersionsApi distributorVersions() {
        return new DistributorVersionsApi(this.client);
    }

    public EntitlementClient entitlements() {
        return new EntitlementClient(this.client);
    }

    public EnvironmentClient environments() {
        return new EnvironmentClient(this.client);
    }

    public GuestIdsApi guestIds() {
        return new GuestIdsApi(this.client);
    }

    public HostedTestApi hosted() {
        return new HostedTestApi(this.client);
    }

    public HypervisorsApi hypervisors() {
        return new HypervisorsApi(this.client);
    }

    public JobsClient jobs() {
        return new JobsClient(this.client);
    }

    public OwnerClient owners() {
        return new OwnerClient(this.client);
    }

    public OwnerContentApi ownerContent() {
        return new OwnerContentApi(this.client);
    }

    public OwnerProductApi ownerProducts() {
        return new OwnerProductApi(this.client);
    }

    public PoolsClient pools() {
        return new PoolsClient(this.client);
    }

    public ProductsApi products() {
        return new ProductsApi(this.client);
    }

    public RolesApi roles() {
        return new RolesApi(this.client);
    }

    public RootApi root() {
        return new RootApi(this.client);
    }

    public StatusApi status() {
        return new StatusApi(this.client);
    }

    public SubscriptionApi subscriptions() {
        return new SubscriptionApi(this.client);
    }

    public UsersApi users() {
        return new UsersApi(this.client);
    }

    public RulesClient rules() {
        return new RulesClient(this.client);
    }
}
