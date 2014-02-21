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
package org.candlepin.resource.test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.resource.ContentResource;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;
/**
 * ContentResourceTest
 */
public class ContentResourceTest {

    private ContentCurator cc;
    private ContentResource cr;
    private I18n i18n;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductServiceAdapter productAdapter;

    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        cc = mock(ContentCurator.class);
        envContentCurator = mock(EnvironmentContentCurator.class);
        poolManager = mock(PoolManager.class);
        productAdapter = mock(ProductServiceAdapter.class);
        cr = new ContentResource(cc, i18n, new DefaultUniqueIdGenerator(),
            envContentCurator, poolManager, productAdapter);
    }

    @Test
    public void listContent() {
        cr.list();
        verify(cc, atLeastOnce()).listAll();
    }

    @Test(expected = BadRequestException.class)
    public void getContentNull() {
        when(cc.find(anyLong())).thenReturn(null);
        cr.getContent("10");
    }

    @Test
    public void getContent() {
        Content content = mock(Content.class);
        when(cc.find(eq("10"))).thenReturn(content);
        assertEquals(content, cr.getContent("10"));
    }

    @Test
    public void createContent() {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(content);
        assertEquals(content, cr.createContent(content));
    }

    @Test
    public void createContentNull()  {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq(10L))).thenReturn(null);
        cr.createContent(content);
        verify(cc, atLeastOnce()).create(content);
    }

    @Test
    public void deleteContent() {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(content);
        EnvironmentContent ec =
            new EnvironmentContent(mock(Environment.class), content.getId(), true);
        List<EnvironmentContent> envContents = listFrom(ec);
        when(envContentCurator.lookupByContent(content.getId())).thenReturn(envContents);

        cr.remove("10");

        verify(cc, atLeastOnce()).delete(eq(content));
        verify(envContentCurator, atLeastOnce()).delete(eq(ec));
    }

    @Test(expected = BadRequestException.class)
    public void deleteContentNull() {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn("10");
        when(cc.find(eq("10"))).thenReturn(null);
        cr.remove("10");
        verify(cc, never()).delete(eq(content));
    }

    @Test
    public void testUpdateContent() {
        final String contentId = "10";
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(contentId);

        when(cc.find(any(String.class))).thenReturn(content);
        when(cc.createOrUpdate(any(Content.class))).thenReturn(content);
        when(productAdapter.getProductsWithContent(
            eq(setFrom(contentId)))).thenReturn(setFrom("productid"));

        cr.updateContent(contentId, content);

        verify(cc).find(eq(contentId));
        verify(cc).createOrUpdate(eq(content));
        verify(productAdapter).getProductsWithContent(setFrom(contentId));
        verify(poolManager).regenerateCertificatesOf(eq("productid"), eq(true));
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateContentThrowsExceptionWhenContentDoesNotExist() {
        Content content = mock(Content.class);
        when(cc.find(any(String.class))).thenReturn(null);

        cr.updateContent("someId", content);
    }

    private <T> List<T> listFrom(T anElement) {
        List<T> l = new ArrayList<T>();
        l.add(anElement);
        return l;
    }

    private <T> Set<T> setFrom(T anElement) {
        Set<T> l = new HashSet<T>();
        l.add(anElement);
        return l;
    }

    private class SetContaining extends ArgumentMatcher<Set<String>> {
        private Collection shouldContain;

        public SetContaining(Collection shouldContain) {
            this.shouldContain = shouldContain;
        }
        public boolean matches(Object set) {
            return ((Set) set).containsAll(shouldContain);
        }
    }
}
