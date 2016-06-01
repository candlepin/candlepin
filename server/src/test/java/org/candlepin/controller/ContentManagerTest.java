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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collection;



/**
 * ContentManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ContentManagerTest {

    private Configuration config;

    @Mock private ContentCurator mockContentCurator;
    @Mock private ProductCurator mockProductCurator;
    @Mock private ProductManager mockProductManager;
    @Mock private EntitlementCertificateGenerator mockEntCertGenerator;

    private ContentManager contentManager;

    @Before
    public void init() throws Exception {
        this.config = new CandlepinCommonTestConfig();

        this.contentManager = new ContentManager(
            this.mockContentCurator, this.mockProductCurator, this.mockProductManager,
            this.mockEntCertGenerator, this.config
        );

        doAnswer(returnsFirstArg()).when(this.mockContentCurator).merge(any(Content.class));
        doAnswer(returnsFirstArg()).when(this.mockContentCurator).create(any(Content.class));
        doAnswer(returnsSecondArg()).when(this.mockContentCurator)
            .updateOwnerContentReferences(any(Content.class), any(Content.class), any(Collection.class));
    }

    @Test
    public void testCreateContent() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1");

        Content output = this.contentManager.createContent(content, owner);

        assertEquals(output, content);
        verify(this.mockContentCurator, times(1)).create(eq(content));
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateContentThatAlreadyExists() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1");

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);

        Content output = this.contentManager.createContent(content, owner);
    }

    @Test
    public void testCreateContentMergeWithExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");

        Content content1 = TestUtil.createContent(owner1, "c1", "test content");
        Content content2 = TestUtil.createContent(owner2, "c1", "test content");

        when(this.mockContentCurator.getContentByVersion(eq(content2.getId()), eq(content2.hashCode())))
            .thenReturn(Arrays.asList(content2));

        Content output = this.contentManager.createContent(content1, owner1);

        assertEquals(output, content2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verify(this.mockContentCurator, times(1)).merge(eq(content2));
        verify(this.mockContentCurator, never()).create(eq(content1));
    }

    @Test
    public void testUpdateContentNoChange() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);

        Content output = this.contentManager.updateContent(content, owner, true);

        assertTrue(output == content);

        verify(this.mockContentCurator, times(1)).merge(eq(content));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateContent() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");
        content.setUuid("test-uuid");

        Content update = TestUtil.createContent(owner, "c1", "new content name");
        update.setUuid("test-uuid");

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);

        Content output = this.contentManager.updateContent(update, owner, false);

        assertEquals(output, update);

        verify(this.mockContentCurator, times(1)).merge(eq(content));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateContentWithCertRegeneration() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");
        content.setUuid("test-uuid");

        Content update = TestUtil.createContent(owner, "c1", "new content name");
        update.setUuid("test-uuid");

        Product product = TestUtil.createProduct("p1", "test product", owner);

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(Arrays.asList(content.getUuid()))))
            .thenReturn(Arrays.asList(product));

        Content output = this.contentManager.updateContent(update, owner, true);

        assertEquals(output, update);

        verify(this.mockContentCurator, times(1)).merge(eq(content));
        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(product)), anyBoolean());
    }

    @Test
    public void testUpdateContentConvergeWithExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content1 = TestUtil.createContent(owner1, "c1", "content-1");
        Content content2 = TestUtil.createContent(owner2, "c1", "updated content");
        Content update = TestUtil.createContent(owner1, "c1", "updated content");
        Product product = TestUtil.createProduct("p1", "test product", owner1);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content1.getId()))).thenReturn(content1);
        when(this.mockContentCurator.lookupById(eq(owner2), eq(content2.getId()))).thenReturn(content2);
        when(this.mockContentCurator.getContentByVersion(eq(update.getId()), eq(update.hashCode())))
            .thenReturn(Arrays.asList(content2));
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content1.getId()))))
            .thenReturn(Arrays.asList(product));

        Content output = this.contentManager.updateContent(update, owner1, false);

        assertTrue(output == content2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1)).updateProduct(any(Product.class), eq(owner1), eq(false));
    }

    @Test
    public void testUpdateContentConvergeWithExistingWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content1 = TestUtil.createContent(owner1, "c1", "content-1");
        Content content2 = TestUtil.createContent(owner2, "c1", "updated content");
        Content update = TestUtil.createContent(owner1, "c1", "updated content");
        Product product = TestUtil.createProduct("p1", "test product", owner1);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content1.getId()))).thenReturn(content1);
        when(this.mockContentCurator.lookupById(eq(owner2), eq(content2.getId()))).thenReturn(content2);
        when(this.mockContentCurator.getContentByVersion(eq(update.getId()), eq(update.hashCode())))
            .thenReturn(Arrays.asList(content2));
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content1.getId()))))
            .thenReturn(Arrays.asList(product));

        Content output = this.contentManager.updateContent(update, owner1, true);

        assertTrue(output == content2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1)).updateProduct(eq(product), eq(owner1), eq(true));
    }

    @Test
    public void testUpdateContentDivergeFromExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content = TestUtil.createContent(owner1, "c1", "content-1");
        content.addOwner(owner2);
        Content update = TestUtil.createContent(owner1, "c1", "updated content");
        Product product = TestUtil.createProduct("p1", "test product", owner1);
        // product.addOwner(owner2);
        product.addContent(content);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        Content output = this.contentManager.updateContent(update, owner1, false);

        assertTrue(output != content);
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));
        assertFalse(content.getOwners().contains(owner1));
        assertTrue(content.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1)).updateProduct(any(Product.class), eq(owner1), eq(false));
    }

    @Test
    public void testUpdateContentDivergeFromExistingWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content = TestUtil.createContent(owner1, "c1", "content-1");
        content.addOwner(owner2);
        Content update = TestUtil.createContent(owner1, "c1", "updated content");
        Product product = TestUtil.createProduct("p1", "test product", owner1);
        // product.addOwner(owner2);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        Content output = this.contentManager.updateContent(update, owner1, true);

        assertTrue(output != content);
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));
        assertFalse(content.getOwners().contains(owner1));
        assertTrue(content.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1)).updateProduct(eq(product), eq(owner1), eq(true));
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateContentThatDoesntExist() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");

        this.contentManager.updateContent(content, owner, false);
    }

    @Test
    public void testRemoveContent() {
        Owner owner = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Content content = TestUtil.createContent(owner, "c1", "content-1");
        Product product = TestUtil.createProduct("p1", "test prod", owner);

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        this.contentManager.removeContent(content, owner, false);

        assertFalse(content.getOwners().contains(owner));

        verify(this.mockProductManager, times(1))
            .removeProductContent(eq(product), eq(Arrays.asList(content)), eq(owner), eq(false));
        verify(this.mockContentCurator, never()).merge(eq(content));
        verify(this.mockContentCurator, times(1)).delete(eq(content));
    }

    @Test
    public void testRemoveContentWithCertRegeneration() {
        Owner owner = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Content content = TestUtil.createContent(owner, "c1", "content-1");
        Product product = TestUtil.createProduct("p1", "test prod", owner);

        when(this.mockContentCurator.lookupById(eq(owner), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        this.contentManager.removeContent(content, owner, true);

        assertFalse(content.getOwners().contains(owner));

        verify(this.mockProductManager, times(1))
            .removeProductContent(eq(product), eq(Arrays.asList(content)), eq(owner), eq(true));
        verify(this.mockContentCurator, never()).merge(eq(content));
        verify(this.mockContentCurator, times(1)).delete(eq(content));
    }

    @Test
    public void testRemoveContentDivergeFromExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content = TestUtil.createContent(owner1, "c1", "content-1");
        content.addOwner(owner2);
        Product product = TestUtil.createProduct("p1", "test prod", owner1);
        // product.addOwner(owner2);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        this.contentManager.removeContent(content, owner1, false);

        assertFalse(content.getOwners().contains(owner1));
        assertTrue(content.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1))
            .removeProductContent(eq(product), eq(Arrays.asList(content)), eq(owner1), eq(false));
        verify(this.mockContentCurator, times(1)).merge(eq(content));
        verify(this.mockContentCurator, never()).delete(eq(content));
    }

    @Test
    public void testRemoveContentDivergeFromExistingWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Content content = TestUtil.createContent(owner1, "c1", "content-1");
        content.addOwner(owner2);
        Product product = TestUtil.createProduct("p1", "test prod", owner1);
        // product.addOwner(owner2);

        when(this.mockContentCurator.lookupById(eq(owner1), eq(content.getId()))).thenReturn(content);
        when(this.mockProductCurator.getProductsWithContent(eq(owner1), eq(Arrays.asList(content.getId()))))
            .thenReturn(Arrays.asList(product));

        this.contentManager.removeContent(content, owner1, true);

        assertFalse(content.getOwners().contains(owner1));
        assertTrue(content.getOwners().contains(owner2));

        verify(this.mockProductManager, times(1))
            .removeProductContent(eq(product), eq(Arrays.asList(content)), eq(owner1), eq(true));
        verify(this.mockContentCurator, times(1)).merge(eq(content));
        verify(this.mockContentCurator, never()).delete(eq(content));
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveContentThatDoesntExist() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");

        this.contentManager.removeContent(content, owner, false);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveContentThatDoesntExistWithRegeneration() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent(owner, "c1", "content-1");

        this.contentManager.removeContent(content, owner, true);
    }
}
