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
package org.candlepin.resource;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/**
 * OwnerContentResourceTest
 */
public class OwnerContentResourceTest {

    private ContentCurator cc;
    private OwnerContentResource ocr;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private OwnerCurator oc;

    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        cc = mock(ContentCurator.class);
        envContentCurator = mock(EnvironmentContentCurator.class);
        poolManager = mock(PoolManager.class);
        oc = mock(OwnerCurator.class);
        productCurator = mock(ProductCurator.class);

        ocr = new OwnerContentResource(cc, i18n, new DefaultUniqueIdGenerator(),
            envContentCurator, poolManager, productCurator, oc);

    }

    @Test
    public void listContent() {
        Owner owner = mock(Owner.class);
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);

        ocr.list("owner");
        verify(cc, atLeastOnce()).listByOwner(eq(owner));
    }

    @Test(expected = NotFoundException.class)
    public void getContentNull() {
        Owner owner = mock(Owner.class);
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);

        when(cc.lookupById(any(Owner.class), anyString())).thenReturn(null);
        ocr.getContent("owner", "10");
    }

    @Test
    public void getContent() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq("10"))).thenReturn(content);

        assertEquals(content, ocr.getContent("owner", "10"));
    }

    @Test
    public void createContent() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(content.getId()).thenReturn("10");
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq("10"))).thenReturn(content);
        when(cc.merge(eq(content))).thenReturn(content);

        assertEquals(content, ocr.createContent("owner", content));
    }

    @Test
    public void createContentNull()  {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(content.getId()).thenReturn("10");
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq("10"))).thenReturn(null);

        ocr.createContent("owner", content);
        verify(cc, atLeastOnce()).create(content);
    }

    @Test
    public void deleteContent() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(content.getId()).thenReturn("10");
        when(content.getOwner()).thenReturn(owner);
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq("10"))).thenReturn(content);

        EnvironmentContent ec = new EnvironmentContent(mock(Environment.class), content, true);
        List<EnvironmentContent> envContents = Arrays.asList(ec);
        when(envContentCurator.lookupByContent(owner, content.getId())).thenReturn(envContents);

        ocr.remove("owner", "10");

        verify(cc, atLeastOnce()).delete(eq(content));
        verify(envContentCurator, atLeastOnce()).delete(eq(ec));
    }

    @Test(expected = NotFoundException.class)
    public void deleteContentNull() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(content.getId()).thenReturn("10");
        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq("10"))).thenReturn(null);

        ocr.remove("owner", "10");
        verify(cc, never()).delete(eq(content));
    }

    @Test
    public void testUpdateContent() {
        final String ownerId = "owner";
        final String productId = "productId";
        final String contentId = "10";

        Owner owner = mock(Owner.class);
        Product product = mock(Product.class);
        Content content = mock(Content.class);

        when(product.getId()).thenReturn(productId);
        when(product.getOwner()).thenReturn(owner);
        when(content.getId()).thenReturn(contentId);
        when(content.getOwner()).thenReturn(owner);

        when(oc.lookupByKey(eq(ownerId))).thenReturn(owner);
        when(cc.lookupById(eq(owner), eq(contentId))).thenReturn(content);
        when(cc.createOrUpdate(eq(content))).thenReturn(content);
        when(productCurator.getProductsWithContent(eq(owner), eq(Arrays.asList(contentId))))
            .thenReturn(Arrays.asList(product));

        ocr.updateContent(ownerId, contentId, content);

        verify(cc).lookupById(eq(owner), eq(contentId));
        verify(cc).createOrUpdate(eq(content));
        verify(productCurator).getProductsWithContent(eq(owner), eq(Arrays.asList(contentId)));
        verify(poolManager).regenerateCertificatesOf(eq(owner), eq(productId), eq(true));
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateContentThrowsExceptionWhenOwnerDoesNotExist() {
        Content content = mock(Content.class);
        when(cc.find(any(String.class))).thenReturn(null);

        ocr.updateContent("owner", "someId", content);
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateContentThrowsExceptionWhenContentDoesNotExist() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);

        when(oc.lookupByKey(eq("owner"))).thenReturn(owner);
        when(cc.lookupById(eq(owner), any(String.class))).thenReturn(null);

        ocr.updateContent("owner", "someId", content);
    }
}
