/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ActivationKeyDTO class
 */
public class ActivationKeyDTOTest extends AbstractDTOTest<ActivationKeyDTO> {

    protected Map<String, Object> values;

    public ActivationKeyDTOTest() {
        super(ActivationKeyDTO.class);

        OwnerDTO owner = new OwnerDTO();
        owner.setId("owner_id");
        owner.setKey("owner_key");
        owner.setDisplayName("owner_name");
        owner.setContentPrefix("content_prefix");
        owner.setDefaultServiceLevel("service_level");
        owner.setLogLevel("log_level");
        owner.setAutobindDisabled(true);
        owner.setContentAccessMode("content_access_mode");
        owner.setContentAccessModeList("content_access_mode_list");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Name", "test-name");
        this.values.put("Description", "test-description");
        this.values.put("Owner", owner);
        this.values.put("ReleaseVersion", "test-release-ver");
        this.values.put("ServiceLevel", "test-service-level");
        this.values.put("AutoAttach", true);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());

        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool", null));
        this.values.put("Pools", pools);

        Set<String> prods = new HashSet<>();
        prods.add("test-id-prod");
        this.values.put("ProductIds", prods);

        Set<Map<String, String>> productDTOs = new HashSet<>();
        Map<String, String> productDTO = new HashMap<>();
        productDTO.put("productId", "test-id-prodDto");
        productDTOs.add(productDTO);
        this.values.put("ProductDTOs", productDTOs);

        Set<ContentOverrideDTO> overrides = new HashSet<>();
        overrides.add(new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override")
            .setName("test-name-override")
            .setValue("test-value-override"));
        this.values.put("ContentOverrides", overrides);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

    @Test
    public void testHasProductWithAbsentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        assertFalse(dto.hasProductId("test-id-prod-1"));
    }

    @Test
    public void testHasProductWithPresentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        Set<String> products = new HashSet<>();
        products.add("test-id-prod-2");
        dto.setProductIds(products);

        assertTrue(dto.hasProductId("test-id-prod-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasProductWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.hasProductId(null);
    }

    @Test
    public void testRemoveProductWithAbsentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertFalse(dto.removeProductId("test-id-prod-3"));

        dto.setProductIds(new HashSet<>());
        assertFalse(dto.removeProductId("test-id-prod-3"));
    }

    @Test
    public void testRemoveProductWithPresentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        Set<String> products = new HashSet<>();
        products.add("test-id-prod-4");
        dto.setProductIds(products);

        assertTrue(dto.removeProductId("test-id-prod-4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveProductWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.removeProductId(null);
    }

    @Test
    public void testAddProductWithAbsentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertTrue(dto.addProductId("test-id-prod-5"));
    }

    @Test
    public void testAddProductWithPresentProduct() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertTrue(dto.addProductId("test-id-prod-6"));

        assertFalse(dto.addProductId("test-id-prod-6"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddProductWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.addProductId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddProductWithEmptyProductId() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertTrue(dto.addProductId(""));
    }

    @Test
    public void testHasPoolWithAbsentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertFalse(dto.hasPool("test-id-pool-1"));
    }

    @Test
    public void testHasPoolWithPresentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-2", null));
        dto.setPools(pools);

        assertTrue(dto.hasPool("test-id-pool-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasPoolWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.hasPool(null);
    }

    @Test
    public void testRemovePoolWithAbsentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        assertFalse(dto.removePool("test-id-pool-3"));

        dto.setPools(new HashSet<>());
        assertFalse(dto.removePool("test-id-pool-3"));
    }

    @Test
    public void testRemovePoolWithPresentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        Set<ActivationKeyDTO.ActivationKeyPoolDTO> pools = new HashSet<>();
        pools.add(new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-4", null));
        dto.setPools(pools);

        assertTrue(dto.removePool("test-id-pool-4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemovePoolWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.removePool(null);
    }

    @Test
    public void testAddPoolWithAbsentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ActivationKeyDTO.ActivationKeyPoolDTO akPool =
            new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-5", null);

        assertTrue(dto.addPool(akPool));
    }

    @Test
    public void testAddPoolWithPresentPool() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ActivationKeyDTO.ActivationKeyPoolDTO akPool =
            new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-6", null);

        assertTrue(dto.addPool(akPool));

        ActivationKeyDTO.ActivationKeyPoolDTO akPool2 =
            new ActivationKeyDTO.ActivationKeyPoolDTO("test-id-pool-6", null);

        assertFalse(dto.addPool(akPool2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddPoolWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.addPool(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddPoolWithEmptyPoolId() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ActivationKeyDTO.ActivationKeyPoolDTO akPool =
            new ActivationKeyDTO.ActivationKeyPoolDTO("", null);
        assertTrue(dto.addPool(akPool));
    }

    @Test
    public void testRemoveContentOverrideWithAbsentContentOverride() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ContentOverrideDTO override = new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-3")
            .setName("test-name-override-3")
            .setValue("test-value-override-3");
        assertFalse(dto.removeContentOverride(override));

        dto.setContentOverrides(new HashSet<>());
        assertFalse(dto.removeContentOverride(override));
    }

    @Test
    public void testRemoveContentOverrideWithPresentContentOverride() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        Set<ContentOverrideDTO> overrides = new HashSet<>();
        overrides.add(new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-4")
            .setName("test-name-override-4")
            .setValue("test-value-override-4"));
        dto.setContentOverrides(overrides);

        ContentOverrideDTO override = new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-4")
            .setName("test-name-override-4")
            .setValue("test-value-override-4");

        assertTrue(dto.removeContentOverride(override));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveContentOverrideWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.removeContentOverride(null);
    }

    @Test
    public void testAddContentOverrideWithAbsentContentOverride() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ContentOverrideDTO override = new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-5")
            .setName("test-name-override-5")
            .setValue("test-value-override-5");

        assertTrue(dto.addContentOverride(override));
    }

    @Test
    public void testAddContentOverrideWithPresentContentOverride() {
        ActivationKeyDTO dto = new ActivationKeyDTO();

        ContentOverrideDTO override = new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-6")
            .setName("test-name-override-6")
            .setValue("test-value-override-6");
        assertTrue(dto.addContentOverride(override));

        ContentOverrideDTO override2 = new ContentOverrideDTO()
            .setContentLabel("test-contentLabel-override-6")
            .setName("test-name-override-6")
            .setValue("test-value-override-6");

        assertFalse(dto.addContentOverride(override2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddContentOverrideWithNullInput() {
        ActivationKeyDTO dto = new ActivationKeyDTO();
        dto.addContentOverride(null);
    }
}
