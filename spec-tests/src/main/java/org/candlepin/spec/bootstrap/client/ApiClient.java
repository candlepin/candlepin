/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import org.candlepin.resource.ActivationKeyApi;
import org.candlepin.resource.AdminApi;
import org.candlepin.resource.CdnApi;
import org.candlepin.resource.ConsumerTypeApi;
import org.candlepin.resource.DeletedConsumerApi;
import org.candlepin.resource.EntitlementsApi;
import org.candlepin.resource.EnvironmentApi;
import org.candlepin.resource.OwnerContentApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.resource.PoolsApi;
import org.candlepin.resource.ProductsApi;
import org.candlepin.resource.RolesApi;
import org.candlepin.resource.RootApi;
import org.candlepin.resource.StatusApi;
import org.candlepin.resource.UsersApi;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * An entrypoint of Candlepin client. Serves as a crossroads between specific APIs.
 */
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule());

    private final org.candlepin.ApiClient client;

    public ApiClient(org.candlepin.ApiClient client) {
        this.client = Objects.requireNonNull(client);
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

    public ConsumerClient consumers() {
        return new ConsumerClient(this.client, MAPPER);
    }

    public ConsumerTypeApi consumerTypes() {
        return new ConsumerTypeApi(this.client);
    }

    public DeletedConsumerApi deletedConsumers() {
        return new DeletedConsumerApi(this.client);
    }

    public EntitlementsApi entitlements() {
        return new EntitlementsApi(this.client);
    }

    public EnvironmentApi environments() {
        return new EnvironmentApi(this.client);
    }

    public JobsClient jobs() {
        return new JobsClient(this.client);
    }

    public OwnerClient owners() {
        return new OwnerClient(this.client);
    }

    public OwnerProductApi ownerProducts() {
        return new OwnerProductApi(this.client);
    }

    public PoolsApi pools() {
        return new PoolsApi(this.client);
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

    public UsersApi users() {
        return new UsersApi(this.client);
    }

    public OwnerContentApi ownerContent() {
        return new OwnerContentApi(this.client);
    }

}
