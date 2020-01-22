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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;

/**
 * DeletedConsumerResource
 */
public class DeletedConsumerResource implements DeletedConsumersApi {
    private DeletedConsumerCurator deletedConsumerCurator;
    private ModelTranslator translator;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator,
        ModelTranslator translator) {

        this.deletedConsumerCurator = deletedConsumerCurator;
        this.translator = translator;
    }

    @Override
    public CandlepinQuery<DeletedConsumerDTO> listByDate(String dateStr) {
        return this.translator.translateQuery(dateStr != null ?
            this.deletedConsumerCurator.findByDate(ResourceDateParser.parseDateString(dateStr)) :
            this.deletedConsumerCurator.listAll(), DeletedConsumerDTO.class);
    }
}
