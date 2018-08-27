/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.ImportRecord;



/**
 * The ImportRecordTranslator provides translation from ImportRecord model objects to
 * ImportRecordDTOs
 */
public class ImportRecordTranslator extends TimestampedEntityTranslator<ImportRecord, ImportRecordDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO translate(ImportRecord source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO translate(ModelTranslator translator, ImportRecord source) {
        return source != null ? this.populate(translator, source, new ImportRecordDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO populate(ImportRecord source, ImportRecordDTO dest) {

        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportRecordDTO populate(ModelTranslator translator, ImportRecord source, ImportRecordDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setStatusMessage(source.getStatusMessage());
        dest.setFileName(source.getFileName());
        dest.setGeneratedBy(source.getGeneratedBy());
        dest.setGeneratedDate(source.getGeneratedDate());

        ImportRecord.Status status = source.getStatus();
        dest.setStatus(status != null ? status.name() : null);

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            dest.setUpstreamConsumer(
                translator.translate(source.getUpstreamConsumer(), ImportUpstreamConsumerDTO.class));
        }
        else {
            dest.setUpstreamConsumer(null);
        }

        return dest;
    }
}
