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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractDTOTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;



/**
 * Test suite for the ContentDTO class
 */
public class ContentDTOTest extends AbstractDTOTest<ContentDTO> {

    protected Map<String, Object> values;

    public ContentDTOTest() {
        super(ContentDTO.class);

        this.values = new HashMap<String, Object>();
        this.values.put("Uuid", "test_value");
        this.values.put("Id", "test_value");
        this.values.put("Type", "test_value");
        this.values.put("Label", "test_value");
        this.values.put("Name", "test_value");
        this.values.put("Vendor", "test_value");
        this.values.put("ContentUrl", "test_value");
        this.values.put("RequiredTags", "test_value");
        this.values.put("ReleaseVersion", "test_value");
        this.values.put("GpgUrl", "test_value");
        this.values.put("MetadataExpire", 1234L);
        this.values.put("ModifiedProductIds", Arrays.asList("1", "2", "3"));
        this.values.put("Arches", "test_value");
        this.values.put("Locked", Boolean.TRUE);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

    @Test
    public void testAddModifiedProducts() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.addModifiedProductId("3");
        assertEquals(new HashSet(Arrays.asList("1", "2", "3")), dto.getModifiedProductIds());
    }

    @Test
    public void testAddModifiedProductsNoChange() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.addModifiedProductId("2");
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddModifiedProductsWithNullValue() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.addModifiedProductId(null);
    }

    @Test
    public void testRemoveModifiedProducts() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.removeModifiedProductId("3");
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());
    }

    @Test
    public void testRemoveModifiedProductsNoChange() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.removeModifiedProductId("3");
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveModifiedProductsWithNullValue() {
        ContentDTO dto = new ContentDTO();
        dto.setModifiedProductIds(Arrays.asList("1", "2"));
        assertEquals(new HashSet(Arrays.asList("1", "2")), dto.getModifiedProductIds());

        dto.removeModifiedProductId(null);
    }
}
