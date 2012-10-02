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
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.resource.ContentResource;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.junit.Before;
import org.junit.Test;
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

    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        cc = mock(ContentCurator.class);
        envContentCurator = mock(EnvironmentContentCurator.class);
        cr = new ContentResource(cc, i18n, new DefaultUniqueIdGenerator(),
            envContentCurator);
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
        List<EnvironmentContent> envContents = new LinkedList<EnvironmentContent>();
        EnvironmentContent ec = new EnvironmentContent(mock(Environment.class),
            content.getId(), true);
        envContents.add(ec);
        when(envContentCurator.lookupByAndContent(content.getId())).thenReturn(envContents);
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
        Content content = mock(Content.class);
        when(cc.update(any(Content.class))).thenReturn(content);

        cr.updateContent(content);

        verify(cc, atLeastOnce()).update(eq(content));
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateContentThrowsExceptionWhenContentDoesNotExist() {
        Content content = mock(Content.class);
        when(cc.update(any(Content.class))).thenReturn(null);

        cr.updateContent(content);
    }
}
