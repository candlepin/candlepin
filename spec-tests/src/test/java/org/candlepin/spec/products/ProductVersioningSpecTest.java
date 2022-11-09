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

package org.candlepin.spec.products;


import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class ProductVersioningSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerContentApi ownerContentApi;
    private static OwnerProductApi ownerProductApi;


    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldCreateOneProductInstanceWhenSharedByMultipleOrgs() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());
    }

    @Test
    public void shouldCreateTwoDistinctProductInstancesWhenDetailsDiffer() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name + "2"));
        assertNotEquals(prod1.getUuid(), prod2.getUuid());
    }

    @Test
    public void shouldCreateANewInstanceWhenMakingChangesToAnExistingInstance() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        assertNotNull(prod1);
        ProductDTO prod2 = ownerProductApi.updateProductByOwner(owner1.getKey(), prod1.getId(),
            new ProductDTO().name("new product name"));
        assertThat(prod2).isNotNull()
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid);

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .returns(prod2.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldCreateANewProductInstanceWhenAnOrgUpdatesASharedInstance() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod3 = ownerProductApi.createProductByOwner(owner3.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());
        assertEquals(prod1.getUuid(), prod3.getUuid());

        ProductDTO prod4 = ownerProductApi.updateProductByOwner(owner2.getKey(), id,
            new ProductDTO().name("new product name"));
        assertNotEquals(prod4.getUuid(), prod1.getUuid());

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod4.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod1.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner3.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod3.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldConvergeProductsWhenAGivenVersionAlreadyExists() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner3 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod3 = ownerProductApi.createProductByOwner(owner3.getKey(),
            Products.random().id(id).name("differing product name"));
        assertEquals(prod1.getUuid(), prod2.getUuid());
        assertNotEquals(prod1.getUuid(), prod3.getUuid());

        ProductDTO prod4 = ownerProductApi.updateProductByOwner(owner3.getKey(), id,
            new ProductDTO().name(name));
        assertEquals(prod4.getUuid(), prod1.getUuid());

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod1.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner3.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod3.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldDeletesProductsWithoutAffectingOtherOrgs() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());

        ownerProductApi.deleteProductByOwner(owner1.getKey(), id);
        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).hasSize(0);
        assertNotFound(() -> ownerProductApi.getProductByOwner(owner1.getKey(), id));

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod2.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldCreatesNewProductsWhenAddingContentToSharedProducts() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());

        String contentId = StringUtil.random("content");
        String contentName = "shared_content";
        String contentLabel = "shared content";
        String contentType = "shared_content_type";
        String contentVendor = "generous vendor";

        ContentDTO content1 = ownerContentApi.createContent(owner1.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));

        ProductDTO prod3 = ownerProductApi.addContent(owner1.getKey(), id, contentId, true);
        assertNotEquals(prod3.getUuid(), prod1.getUuid());
        assertNotEquals(prod3.getUuid(), prod2.getUuid());

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod2.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod3.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldCreatesNewProductsWhenUpdatingContentUsedBySharedProducts() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());

        String contentId = StringUtil.random("content");
        String contentName = "shared_content";
        String contentLabel = "shared content";
        String contentType = "shared_content_type";
        String contentVendor = "generous vendor";

        ContentDTO content1 = ownerContentApi.createContent(owner1.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        ContentDTO content2 = ownerContentApi.createContent(owner2.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        assertEquals(content1.getUuid(), content2.getUuid());

        ProductDTO prod3 = ownerProductApi.addContent(owner1.getKey(), id, content1.getId(), true);
        ProductDTO prod4 = ownerProductApi.addContent(owner2.getKey(), id, content1.getId(), true);
        assertThat(prod3)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid)
            .returns(prod4.getUuid(), ProductDTO::getUuid);
        assertThat(prod4)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod4.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        // Actual test starts here
        ContentDTO content3 = ownerContentApi.updateContent(owner1.getKey(), contentId,
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label("new label")
            .type(contentType)
            .vendor(contentVendor));
        assertThat(prod3)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod4.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod4.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldCreateNewProductsWhenRemovingContentUsedBySharedProducts() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());

        String contentId = StringUtil.random("content");
        String contentName = "shared_content";
        String contentLabel = "shared content";
        String contentType = "shared_content_type";
        String contentVendor = "generous vendor";

        ContentDTO content1 = ownerContentApi.createContent(owner1.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        ContentDTO content2 = ownerContentApi.createContent(owner2.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        assertEquals(content1.getUuid(), content2.getUuid());

        ProductDTO prod3 = ownerProductApi.addContent(owner1.getKey(), id, content1.getId(), true);
        ProductDTO prod4 = ownerProductApi.addContent(owner2.getKey(), id, content1.getId(), true);
        assertThat(prod3)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid)
            .returns(prod4.getUuid(), ProductDTO::getUuid);
        assertThat(prod4)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod4.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        // Actual test starts here
        ownerContentApi.remove(owner1.getKey(), contentId);
        prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull().singleElement();

        // This is an interesting case. Due to our current implementation, these will be equal, but it's
        // by coincedence, not intent. At the time of writing, it is not a requirement for a given state
        // to have a defined version/hash, but it does. As a result, we can't test that they shouldn't
        // be equal (because they will be), but testing that they are equal is wrong. We'll just comment
        // these out and leave this message here for future maintainers.
        // assertNotEquals(prod1.getUuid(), prods.get(0).getUuid());
        // assertNotEquals(prod2.getUuid(), prods.get(0).getUuid());
        assertThat(prods).isNotNull()
            .singleElement()
            .doesNotReturn(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod4.getUuid(), ProductDTO::getUuid);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .returns(prod4.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldConvergeIdenticalProductsWhenConvergingContent() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String id = StringUtil.random("id");
        String name = StringUtil.random("name");

        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(),
            Products.random().id(id).name(name));
        ProductDTO prod2 = ownerProductApi.createProductByOwner(owner2.getKey(),
            Products.random().id(id).name(name));
        assertEquals(prod1.getUuid(), prod2.getUuid());

        String contentId = StringUtil.random("content");
        String contentName = "shared_content";
        String contentLabel = "shared content";
        String contentType = "shared_content_type";
        String contentVendor = "generous vendor";

        ContentDTO content1 = ownerContentApi.createContent(owner1.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        ContentDTO content2 = ownerContentApi.createContent(owner2.getKey(),
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label("different label")
            .type(contentType)
            .vendor(contentVendor));
        assertNotEquals(content1.getUuid(), content2.getUuid());

        ProductDTO prod3 = ownerProductApi.addContent(owner1.getKey(), id, contentId, true);
        ProductDTO prod4 = ownerProductApi.addContent(owner2.getKey(), id, contentId, true);
        assertThat(prod3)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        // Another interesting case here. While it may appear that these should be different, the
        // addition of the content to prod1 triggered the generation of a new product for org1 (prod3),
        // leaving org2 as the only owner for prod2. That being the case, prod2 was updated in-place,
        // allowing it to retain its UUID, and make this test look strange
        // NOTE: The above will only be true when in-place updates are enabled. When they are disabled
        // (as they are now), the UUIDs will not match, since we're always forking on any change.
        assertThat(prod4)
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid) // ^
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid) // ^
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid);

        // Actual test starts here
        ContentDTO content3 = ownerContentApi.updateContent(owner2.getKey(), contentId,
            new ContentDTO()
            .name(contentName)
            .id(contentId)
            .label(contentLabel)
            .type(contentType)
            .vendor(contentVendor));
        assertEquals(content3.getUuid(), content1.getUuid());
        assertNotEquals(content3.getUuid(), content2.getUuid());

        List<ProductDTO> prods = ownerProductApi.getProductsByOwner(owner1.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid)
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod4.getUuid(), ProductDTO::getUuid);
        ProductDTO prod5 = prods.get(0);

        prods = ownerProductApi.getProductsByOwner(owner2.getKey(), new ArrayList<>());
        assertThat(prods).isNotNull()
            .singleElement()
            .doesNotReturn(prod1.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod2.getUuid(), ProductDTO::getUuid)
            .returns(prod3.getUuid(), ProductDTO::getUuid)
            .doesNotReturn(prod4.getUuid(), ProductDTO::getUuid)
            .returns(prod5.getUuid(), ProductDTO::getUuid);
    }

    @Test
    public void shouldCleanupOrphansWithoutInterferingWithNormalActions() throws Exception {
        // NOTE:
        // This test takes advantage of the immutable nature of products with the in-place update branch
        // disabled. If in-place updates are ever reenabled, we'll need a way to generate large numbers
        // of orphaned products for this test.

        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());

        String prefix = "test-product-";
        int offset = 10000;
        int length = 100;

        // Repeat this test a few(ish) times to hopefully catch any synchronization error
        for (int i = 0; i < 10; i++) {
            final int base = offset;
            List<String> o1Uuids = Collections.synchronizedList(new ArrayList<>());
            List<String> o2Uuids = Collections.synchronizedList(new ArrayList<>());

            // Create a bunch of dummy products
            for (int j = base; j < base + length - 1; j++) {
                String id = String.format("%s%d", prefix, j);
                // create product and immediately update it to generate an orphaned product
                ownerProductApi.createProductByOwner(owner1.getKey(), new ProductDTO().name(id).id(id));
            }

            // Attempt to update and create new products to get into some funky race conditions with
            // convergence and orphanization
            Thread updater = new Thread(() -> {
                for (int j = base; j < base + length - 1; j++) {
                    String id = String.format("%s%d", prefix, j);
                    // create product and immediately update it to generate an orphaned product
                    ProductDTO prod = ownerProductApi.updateProductByOwner(owner1.getKey(), id,
                        new ProductDTO().name(id + "-update"));
                    synchronized (o1Uuids) {
                        o1Uuids.add(prod.getUuid());
                    }
                }
            });
            updater.start();
            Thread generator = new Thread(() -> {
                for (int j = base; j < base + length - 1; j++) {
                    String id = String.format("%s%d", prefix, j);
                    ProductDTO prod = ownerProductApi.createProductByOwner(owner2.getKey(),
                        new ProductDTO().name(id).id(id));
                    synchronized (o2Uuids) {
                        o2Uuids.add(prod.getUuid());
                    }
                }
            });
            generator.start();

            sleep(1000);
            AsyncJobStatusDTO job = client.jobs().scheduleJob("OrphanCleanupJob");
            updater.join(90000);
            generator.join(90000);
            AsyncJobStatusDTO jobStatus = client.jobs().waitForJob(job);
            assertEquals("FINISHED", jobStatus.getState());

            // Verify the products created/updated still exist
            assertThat(o1Uuids)
                .map(uuid -> client.products().getProduct(uuid))
                .hasSize(o1Uuids.size());
            assertThat(o2Uuids)
                .map(uuid -> client.products().getProduct(uuid))
                .hasSize(o2Uuids.size());

            offset += length;
        }
    }

    @Test
    public void shouldAddAndRemoveContentWithoutCausingDbWriteIssues() throws Exception {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        List<ContentDTO> contents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ContentDTO content = new ContentDTO()
                .name("name" + i)
                .id("id" + i)
                .label("label" + i)
                .type("type" + i)
                .vendor("vendor" + i);
            contents.add(ownerContentApi.createContent(owner1.getKey(), content));
        }
        // Add and remove content to see if it causes concurrent update issues on the product
        ProductDTO prod1 = ownerProductApi.createProductByOwner(owner1.getKey(), Products.random());
        for (int i = 0; i < 25; i++) {
            List<Thread> threads = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                final int index = j;
                final String prod1Id = prod1.getId();
                threads.add(new Thread(() -> ownerProductApi.addContent(owner1.getKey(), prod1Id,
                    contents.get(index).getId(), true)));
                threads.get(j).start();
            }
            for (int j = 0; j < 10; j++) {
                threads.get(j).join(90000);
            }
            prod1 = ownerProductApi.getProductByOwner(owner1.getKey(), prod1.getId());
            assertThat(prod1.getProductContent()).hasSize(10);

            threads = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                final int index = j;
                final String prod1Id = prod1.getId();
                threads.add(new Thread(() -> ownerProductApi.removeContent(owner1.getKey(), prod1Id,
                    contents.get(index).getId())));
                threads.get(j).start();
            }
            for (int j = 0; j < 10; j++) {
                threads.get(j).join(90000);
            }
            prod1 = ownerProductApi.getProductByOwner(owner1.getKey(), prod1.getId());
            assertThat(prod1.getProductContent()).hasSize(0);
        }
    }

    @FunctionalInterface
    interface UpdateFunction {
        ProductDTO call(OwnerDTO owner, ProductDTO parentProduct, ProductDTO product);
    }

    @Test
    public void shouldSupportNTierProductVersioningForProvidedProducts() {
        UpdateFunction providedProducts = (owner, parentProduct, product) ->
            ownerProductApi.updateProductByOwner(owner.getKey(), parentProduct.getId(), new ProductDTO()
            .providedProducts(Set.of(product)));
        ntierVersioningTest(providedProducts);
    }

    @Test
    public void shouldSupportNTierProductVersioningForDerivedProducts() {
        UpdateFunction derivededProducts = (owner, parentProduct, product) ->
            ownerProductApi.updateProductByOwner(owner.getKey(), parentProduct.getId(), new ProductDTO()
            .derivedProduct(product));
        ntierVersioningTest(derivededProducts);
    }


    private void ntierVersioningTest(UpdateFunction updater) {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO parentProduct = null;
        Map<String, String> prevUuids = new HashMap<>();
        Map<String, String> uuids = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            String pid = String.format("tier%d_prod-%s", i,
                StringUtil.random(8, StringUtil.CHARSET_NUMERIC_HEX));
            ProductDTO product = ownerProductApi.createProductByOwner(owner.getKey(),
                Products.random());
            uuids.put(product.getId(), product.getUuid());

            if (parentProduct != null) {
                updater.call(owner, parentProduct, product);
                for (String prodId : prevUuids.keySet()) {
                    ProductDTO updatedProduct = ownerProductApi.getProductByOwner(owner.getKey(), prodId);
                    uuids.put(updatedProduct.getId(), updatedProduct.getUuid());
                }

            }

            // We expect the size of the uuids collection to grow by 1 every iteration
            assertEquals(uuids.size(), prevUuids.size() + 1);
            // Verify that the uuids are changing every iteration
            // (indicating the version has been recalculated)
            for (String prevId : prevUuids.keySet()) {
                String uuid = uuids.get(prevId);
                assertNotNull(uuid);
                assertNotEquals(uuid, prevUuids.get(prevId));
            }
            parentProduct = product;
            prevUuids = uuids;
            uuids = new HashMap<>();
        }
    }
}
