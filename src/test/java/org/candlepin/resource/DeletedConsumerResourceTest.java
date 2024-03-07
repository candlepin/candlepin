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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.server.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.v1.DeletedConsumerTranslator;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator.DeletedConsumerQueryArguments;
import org.candlepin.paging.PageRequest;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;


@ExtendWith(MockitoExtension.class)
public class DeletedConsumerResourceTest {
    private ModelTranslator modelTranslator;
    private DeletedConsumerCurator deletedConsumerCurator;
    private I18n i18n;

    private DevConfig config;
    private DeletedConsumerResource deletedConsumerResource;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.deletedConsumerCurator = mock(DeletedConsumerCurator.class);
        this.modelTranslator = new SimpleModelTranslator();
        this.modelTranslator.registerTranslator(new DeletedConsumerTranslator(), DeletedConsumer.class,
            DeletedConsumerDTO.class);
        this.deletedConsumerResource = new DeletedConsumerResource(this.deletedConsumerCurator, i18n,
            modelTranslator, config);
    }

    @Test
    public void testListByDate() {
        when(deletedConsumerCurator.listAll(any(DeletedConsumerQueryArguments.class))).
            thenReturn(new LinkedList<>());
        ResteasyContext.pushContext(PageRequest.class,
            new PageRequest()
                .setPage(1)
                .setPerPage(10)
                .setSortBy("created"));
        deletedConsumerResource.listByDate(OffsetDateTime.now(), 1, 10, "asc", "created");
        verify(deletedConsumerCurator, atLeastOnce()).listAll(any(DeletedConsumerQueryArguments.class));
        ResteasyContext.popContextData(PageRequest.class);
    }

    @Test
    public void testGetContentsOverMaxNoPaging() {
        when(deletedConsumerCurator.getDeletedConsumerCount(any(DeletedConsumerQueryArguments.class)))
            .thenReturn(10001L);
        assertThrows(BadRequestException.class, () -> deletedConsumerResource.listByDate(
            null, null, null, null, null));
    }

    @Test
    public void testGetContentsNoPaging() {
        when(deletedConsumerCurator.getDeletedConsumerCount(any(DeletedConsumerQueryArguments.class)))
            .thenReturn(1L);
        when(deletedConsumerCurator.listAll(any(DeletedConsumerQueryArguments.class)))
            .thenReturn(List.of(new DeletedConsumer()));
        assertEquals(1, deletedConsumerResource.listByDate(null, null, null, null, null).toList().size());
    }
}
