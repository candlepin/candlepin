/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.resource.ContentResource;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
/**
 * ContentResourceTest
 */
public class ContentResourceTest {

    private ContentCurator cc; 
    private ContentResource cr;
    private I18n i18n; 
    
    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        cc = mock(ContentCurator.class);
        cr = new ContentResource(cc, i18n);
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
}
