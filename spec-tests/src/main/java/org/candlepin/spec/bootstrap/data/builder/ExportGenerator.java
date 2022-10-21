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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.CdnApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.RolesApi;
import org.candlepin.resource.client.v1.UsersApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportGenerator {

    private final ApiClient client;
    private final OwnerClient ownerApi;
    private final OwnerContentApi ownerContentApi;
    private final OwnerProductApi ownerProductApi;
    private final CdnApi cdnApi;
    private final ConsumerClient consumerApi;
    private final RolesApi rolesApi;
    private final UsersApi usersApi;

    private OwnerDTO owner;
    private ConsumerDTO consumer;
    private ExportCdn cdn;

    public ExportGenerator(ApiClient adminClient) {
        client = adminClient;
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        cdnApi = client.cdns();
        consumerApi = client.consumers();
        rolesApi = client.roles();
        usersApi = client.users();
    }

    /**
     * Creates an export of currently initialized org data.
     *
     * @return export info
     */
    public Export export() {
        File manifest = createExport(consumer.getUuid(), cdn);

        return new Export(manifest, consumer, cdn);
    }

    /**
     * Initializes org with a minimal set of data. Single product, no branding.
     *
     * @return minimal export
     */
    public ExportGenerator minimal() {
        initializeMinimalExport();

        return this;
    }

    private void initializeMinimalExport() {
        owner = ownerApi.createOwner(Owners.random());

        RoleDTO role = rolesApi.createRole(Roles.ownerAll(owner));
        UserDTO user = usersApi.createUser(Users.random());
        rolesApi.addUserToRole(role.getName(), user.getUsername());
        consumer = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin),
            user.getUsername(), owner.getKey(), null, true);

        consumer.putFactsItem("distributor_version", "sam-1.3")
            .releaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerApi.updateConsumer(consumer.getUuid(), consumer);
        consumer = consumerApi.getConsumer(consumer.getUuid());

        CdnDTO cdnDto = cdnApi.createCdn(Cdns.random());
        cdn = Cdns.toExport(cdnDto);
    }

    public ExportGenerator withProduct(ProductDTO product) {
        return withProduct(product, Content.random()
            .metadataExpire(6000L)
            .requiredTags("TAG1,TAG2"));
    }

    public ExportGenerator withProduct(ProductDTO product, ContentDTO content) {
        String ownerKey = owner.getKey();
        ProductDTO createdProduct = ownerProductApi.createProductByOwner(ownerKey, product);
        ContentDTO createdContent = ownerContentApi.createContent(ownerKey, content);
        ownerProductApi.addContent(ownerKey, createdProduct.getId(), createdContent.getId(), true);

        Map<String, PoolDTO> poolIdToPool = createPoolsForProducts(ownerKey, createdProduct);

        Set<String> poolIds = poolIdToPool.values().stream()
            .map(PoolDTO::getId)
            .collect(Collectors.toSet());
        bindPoolsToConsumer(consumerApi, consumer.getUuid(), poolIds);

        return this;
    }

    private File createExport(String consumerUuid, ExportCdn cdn) {
        String cdnLabel = cdn == null ? null : cdn.label();
        String cdnWebUrl = cdn == null ? null : cdn.webUrl();
        String cdnApiUrl = cdn == null ? null : cdn.apiUrl();
        File export = client.consumers().exportData(consumerUuid, cdnLabel, cdnWebUrl, cdnApiUrl);
        export.deleteOnExit();

        return export;
    }

    private void bindPoolsToConsumer(ConsumerClient consumerApi, String consumerUuid,
        Collection<String> poolIds) throws ApiException {
        for (String poolId : poolIds) {
            consumerApi.bindPool(consumerUuid, poolId, 1);
        }
    }

    private Map<String, PoolDTO> createPoolsForProducts(String ownerKey, ProductDTO... products) {
        Set<PoolDTO> collect = Arrays.stream(products)
            .map(product -> createPool(ownerKey, product))
            .collect(Collectors.toSet());

        return collect.stream()
            .collect(Collectors.toMap(PoolDTO::getProductId, Function.identity()));
    }

    private PoolDTO createPool(String ownerKey, ProductDTO product) throws ApiException {
        PoolDTO pool = Pools.random(product)
            .providedProducts(new HashSet<>())
            .contractNumber("")
            .accountNumber("12345")
            .orderNumber("6789")
            .endDate(OffsetDateTime.now().plusYears(5));

        if (product.getBranding() == null || product.getBranding().isEmpty()) {
            pool.setBranding(null);
        }
        else {
            pool.setBranding(new HashSet<>(product.getBranding()));
        }

        return ownerApi.createPool(ownerKey, pool);
    }

    public ConsumerDTO getExportConsumer() {
        return consumer;
    }

}
