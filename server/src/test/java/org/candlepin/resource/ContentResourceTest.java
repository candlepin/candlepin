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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EmptyCandlepinQuery;
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
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;



/**
 * ContentResourceTest
 */
public class ContentResourceTest {

    private ContentCurator cc;
    private ContentResource cr;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private OwnerCurator oc;
    private ModelTranslator modelTranslator;

    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        cc = mock(ContentCurator.class);
        envContentCurator = mock(EnvironmentContentCurator.class);
        poolManager = mock(PoolManager.class);
        oc = mock(OwnerCurator.class);
        productCurator = mock(ProductCurator.class);

        this.modelTranslator = new SimpleModelTranslator();
        this.modelTranslator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);

        cr = new ContentResource(cc, i18n, new DefaultUniqueIdGenerator(), envContentCurator,
            poolManager, productCurator, oc, this.modelTranslator);
    }

    @Test
    public void listContent() {
        when(cc.listAll()).thenReturn(new EmptyCandlepinQuery());

        cr.list();
        verify(cc, atLeastOnce()).listAll();
    }

    @Test(expected = NotFoundException.class)
    public void getContentNull() {
        when(cc.find(anyLong())).thenReturn(null);
        cr.getContent("10");
    }

    @Test
    public void getContent() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        ContentDTO expected = this.modelTranslator.translate(content, ContentDTO.class);

        when(cqmock.list()).thenReturn(Arrays.asList(owner));
        when(oc.listAll()).thenReturn(cqmock);
        when(cc.lookupByUuid(eq("10"))).thenReturn(content);

        ContentDTO output = cr.getContent("10");

        assertEquals(expected, output);
    }

    @Test(expected = BadRequestException.class)
    public void createContent() {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(content);
        assertEquals(content, cr.createContent(content));
    }

    @Test(expected = BadRequestException.class)
    public void createContentNull()  {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq(10L))).thenReturn(null);
        cr.createContent(content);

        verify(cc, never()).create(content);
    }

    @Test(expected = BadRequestException.class)
    public void deleteContent() {
        Owner owner = mock(Owner.class);
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(content);
        EnvironmentContent ec = new EnvironmentContent(mock(Environment.class), content, true);
        List<EnvironmentContent> envContents = Arrays.asList(ec);
        when(envContentCurator.lookupByContent(owner, content.getId())).thenReturn(envContents);

        cr.remove("10");

        verify(cc, never()).delete(eq(content));
        verify(envContentCurator, never()).delete(eq(ec));
    }

    @Test(expected = BadRequestException.class)
    public void deleteContentNull() {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(null);
        cr.remove("10");
        verify(cc, never()).delete(eq(content));
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateContent() {
        final String productId = "productId";
        final String contentId = "10";

        Owner owner = mock(Owner.class);
        Product product = mock(Product.class);
        Content content = mock(Content.class);
        CandlepinQuery cqmock = mock(CandlepinQuery.class);

        when(product.getId()).thenReturn(productId);
        when(content.getId()).thenReturn(contentId);
        when(cqmock.list()).thenReturn(Arrays.asList(product));

        when(cc.find(any(String.class))).thenReturn(content);
        when(cc.merge(any(Content.class))).thenReturn(content);
        when(productCurator.getProductsByContent(eq(owner), eq(Arrays.asList(contentId))))
            .thenReturn(cqmock);

        cr.updateContent(contentId, content);

        verify(cc, never()).find(eq(contentId));
        verify(cc, never()).merge(eq(content));
        verify(productCurator, never()).getProductsByContent(owner, Arrays.asList(contentId));
    }

    @Test(expected = BadRequestException.class)
    public void testUpdateContentThrowsExceptionWhenContentDoesNotExist() {
        Content content = mock(Content.class);
        when(cc.find(any(String.class))).thenReturn(null);

        cr.updateContent("someId", content);
    }
}
