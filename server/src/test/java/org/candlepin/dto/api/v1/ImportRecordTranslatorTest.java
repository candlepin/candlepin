/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.util.Util;

import java.util.Date;



/**
 * Test suite for the ImportRecordTranslator class
 */
public class ImportRecordTranslatorTest extends
    AbstractTranslatorTest<ImportRecord, ImportRecordDTO, ImportRecordTranslator> {

    protected ImportRecordTranslator translator = new ImportRecordTranslator();

    protected ImportUpstreamConsumerTranslatorTest iucTranslatorTest =
        new ImportUpstreamConsumerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.iucTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(this.translator, ImportRecord.class, ImportRecordDTO.class);
    }

    @Override
    protected ImportRecordTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ImportRecord initSourceObject() {
        ImportUpstreamConsumer consumer = this.iucTranslatorTest.initSourceObject();

        ImportRecord source = new ImportRecord(null);

        source.setId("test-id");
        source.recordStatus(ImportRecord.Status.SUCCESS, "test-status-msg");
        source.setFileName("test-filename");
        source.setGeneratedBy("test-source");
        source.setGeneratedDate(new Date());
        source.setUpstreamConsumer(consumer);
        source.setCreated(new Date());
        source.setUpdated(new Date());

        return source;
    }

    @Override
    protected ImportRecordDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ImportRecordDTO();
    }

    @Override
    protected void verifyOutput(ImportRecord source, ImportRecordDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getStatusMessage(), dest.getStatusMessage());
            assertEquals(source.getFileName(), dest.getFileName());
            assertEquals(source.getGeneratedBy(), dest.getGeneratedBy());
            assertEquals(source.getGeneratedDate(),  Util.toDate(dest.getGeneratedDate()));
            assertEquals(source.getCreated(), Util.toDate(dest.getCreated()));
            assertEquals(source.getUpdated(), Util.toDate(dest.getUpdated()));

            ImportRecord.Status status = source.getStatus();
            assertEquals(status != null ? status.toString() : null, dest.getStatus());

            if (childrenGenerated) {
                this.iucTranslatorTest.verifyOutput(
                    source.getUpstreamConsumer(), dest.getUpstreamConsumer(), true);
            }
            else {
                assertNull(dest.getUpstreamConsumer());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
