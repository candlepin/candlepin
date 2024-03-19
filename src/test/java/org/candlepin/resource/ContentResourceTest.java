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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ContentCurator.ContentQueryArguments;
import org.candlepin.paging.PageRequest;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;


/**
 * ContentResourceTest
 */
public class ContentResourceTest {

    private ContentCurator cc;
    private ContentResource resource;
    private I18n i18n;
    private ModelTranslator modelTranslator;
    private DevConfig config;

    @BeforeEach
    public void init() {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.cc = mock(ContentCurator.class);
        this.modelTranslator = new SimpleModelTranslator();
        this.modelTranslator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);
        this.config = TestConfig.defaults();

        this.resource = new ContentResource(this.cc, this.i18n, this.modelTranslator, this.config);
        ResteasyContext.popContextData(PageRequest.class);
    }

    @Test
    public void testGetContents() {
        when(cc.listAll(any(ContentQueryArguments.class))).thenReturn(new LinkedList<>());
        ResteasyContext.pushContext(PageRequest.class,
            new PageRequest()
            .setPage(1)
            .setPerPage(10)
            .setSortBy("label"));
        resource.getContents(1, 10, "asc", "label");
        verify(cc, atLeastOnce()).listAll(any(ContentQueryArguments.class));
        ResteasyContext.popContextData(PageRequest.class);
    }

    @Test
    public void testGetContentsOverMaxNoPaging() {
        when(cc.getContentCount()).thenReturn(10001L);
        assertThrows(BadRequestException.class, () -> resource.getContents(null, null, null, null));
    }

    @Test
    public void testGetContentsNoPaging() {
        when(cc.getContentCount()).thenReturn(1L);
        when(cc.listAll(any(ContentQueryArguments.class))).thenReturn(List.of(new Content()));
        assertEquals(1, resource.getContents(null, null, null, null).toList().size());
    }

    @Test
    public void getContentNull() {
        when(cc.get(anyLong())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> resource.getContentByUuid("10"));
    }

    @Test
    public void getContent() {
        Content content = mock(Content.class);
        ContentDTO expected = this.modelTranslator.translate(content, ContentDTO.class);
        when(cc.getByUuid(eq("10"))).thenReturn(content);

        ContentDTO output = resource.getContentByUuid("10");

        assertEquals(expected, output);
    }
}
