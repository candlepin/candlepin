/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.hamcrest.collection.IsCollectionContaining.hasItem;
import static org.junit.Assert.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;



public class ProductManagerTest extends DatabaseTestFixture {

    // @Test
    // public void testUpdateProduct() {
    //     Product original = createTestProduct();
    //     productCurator.create(original);
    //     // Will have same ID, but we'll modify other data:
    //     Product modified = createTestProduct();
    //     String newName = "new name";
    //     modified.setName(newName);

    //     // Hack up the attributes, keep a1, skip a2, modify a3, add a4:
    //     Set<ProductAttribute> newAttributes = new HashSet<ProductAttribute>();
    //     newAttributes.add(modified.getAttribute("a1"));
    //     ProductAttribute a3 = modified.getAttribute("a3");
    //     a3.setValue("a3-modified");
    //     a3.setProduct(modified);
    //     newAttributes.add(a3);
    //     ProductAttribute a4 = new ProductAttribute("a4", "a4");
    //     a4.setProduct(modified);
    //     newAttributes.add(a4);
    //     modified.setAttributes(newAttributes);

    //     int initialAttrCount = attributeCurator.listAll().size();
    //     productCurator.updateProduct(modified, owner);

    //     Product lookedUp = productCurator.lookupById(owner, original.getId());
    //     assertEquals(newName, lookedUp.getName());
    //     assertEquals(3, lookedUp.getAttributes().size());
    //     assertEquals("a1", lookedUp.getAttributeValue("a1"));
    //     assertEquals("a3-modified", lookedUp.getAttributeValue("a3"));
    //     assertEquals("a4", lookedUp.getAttributeValue("a4"));

    //     // TODO: test content merging

    //     // Old attributes should get cleaned up:
    //     assertEquals(initialAttrCount, attributeCurator.listAll().size());
    // }

    // @Test
    // public void testRemoveProductContent() {
    //     Product p = createTestProduct();
    //     Content content = new Content(this.owner, "test-content", "test-content",
    //         "test-content", "yum", "us", "here", "here", "test-arch");
    //     p.addContent(content);
    //     contentCurator.create(content);
    //     productCurator.create(p);

    //     p = productCurator.find(p.getUuid());
    //     assertEquals(1, p.getProductContent().size());

    //     productCurator.removeProductContent(p, Arrays.asList(content), this.owner);
    //     p = productCurator.find(p.getUuid());
    //     assertEquals(0, p.getProductContent().size());
    // }


    // @Test
    // public void testRemoveProductContent() {
    //     Product p = createTestProduct();
    //     Content content = new Content(this.owner, "test-content", "test-content",
    //         "test-content", "yum", "us", "here", "here", "test-arch");
    //     p.addContent(content);
    //     contentCurator.create(content);
    //     productCurator.create(p);

    //     p = productCurator.find(p.getUuid());
    //     assertEquals(1, p.getProductContent().size());

    //     productCurator.removeProductContent(p, Arrays.asList(content), this.owner);
    //     p = productCurator.find(p.getUuid());
    //     assertEquals(0, p.getProductContent().size());
    // }

}