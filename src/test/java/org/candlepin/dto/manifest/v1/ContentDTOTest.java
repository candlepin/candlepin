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
package org.candlepin.dto.manifest.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


/**
 * Test suite for the ContentDTO (manifest import/export) class
 */
public class ContentDTOTest extends AbstractDTOTest<ContentDTO> {

    protected Map<String, Object> values;

    public ContentDTOTest() {
        super(ContentDTO.class);

        this.values = new HashMap<>();
        this.values.put("Id", "test_value");
        this.values.put("Uuid", "test_value");
        this.values.put("MetadataExpiration", 3L);
        this.values.put("Type", "test_value");
        this.values.put("Label", "test_value");
        this.values.put("Name", "test_value");
        this.values.put("Vendor", "test_value");
        this.values.put("ContentUrl", "test_value");
        this.values.put("RequiredTags", "test_value");
        this.values.put("ReleaseVersion", "test_value");
        this.values.put("GpgUrl", "test_value");
        this.values.put("RequiredProductIds", Arrays.asList("1", "2", "3"));
        this.values.put("Arches", "test_value");
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
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
    public void testAddRequiredProducts() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        dto.addRequiredProductId("3");
        assertEquals(new HashSet<>(Arrays.asList("1", "2", "3")), dto.getRequiredProductIds());
    }

    @Test
    public void testAddRequiredProductsNoChange() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        dto.addRequiredProductId("2");
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());
    }

    @Test
    public void testAddRequiredProductsWithNullValue() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        assertThrows(IllegalArgumentException.class, () -> dto.addRequiredProductId(null));
    }

    @Test
    public void testRemoveRequiredProducts() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        dto.removeRequiredProductId("3");
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());
    }

    @Test
    public void testRemoveRequiredProductsNoChange() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        dto.removeRequiredProductId("3");
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());
    }

    @Test
    public void testRemoveRequiredProductsWithNullValue() {
        ContentDTO dto = new ContentDTO();
        dto.setRequiredProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet<>(Arrays.asList("1", "2")), dto.getRequiredProductIds());

        assertThrows(IllegalArgumentException.class, () -> dto.removeRequiredProductId(null));
    }
}
