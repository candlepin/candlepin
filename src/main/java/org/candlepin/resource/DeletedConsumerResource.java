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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.DeletedConsumerDTO;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resource.server.v1.DeletedConsumerApi;
import org.candlepin.util.Util;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * DeletedConsumerResource
 */
public class DeletedConsumerResource implements DeletedConsumerApi {
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final ModelTranslator translator;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator,
        ModelTranslator translator) {
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.translator = Objects.requireNonNull(translator);
    }

    @Override
    public Stream<DeletedConsumerDTO> listByDate(OffsetDateTime date) {
        List<DeletedConsumer> deletedConsumers = date != null ?
            this.deletedConsumerCurator.findByDate(Util.toDate(date)) :
            this.deletedConsumerCurator.listAll();
        return deletedConsumers.stream()
            .map(this.translator.getStreamMapper(DeletedConsumer.class, DeletedConsumerDTO.class));
    }
}
