/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Certificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.UpstreamConsumer;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UpstreamConsumerArrayElementTranslatorTest extends AbstractTranslatorTest<UpstreamConsumer,
    UpstreamConsumerDTOArrayElement, UpstreamConsumerDTOArrayElementTranslator> {

    protected UpstreamConsumerDTOArrayElementTranslator ustreamConsumertranslator =
        new UpstreamConsumerDTOArrayElementTranslator();

    protected CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(this.ustreamConsumertranslator, UpstreamConsumer.class,
            UpstreamConsumerDTOArrayElement.class);
    }

    @Override
    protected UpstreamConsumerDTOArrayElementTranslator initObjectTranslator() {
        return this.ustreamConsumertranslator;
    }

    @Override
    protected UpstreamConsumer initSourceObject() {
        UpstreamConsumer source = new UpstreamConsumer();
        source.setIdCert((IdentityCertificate) this.certificateTranslatorTest.initSourceObject());
        source.setCreated(new Date());
        source.setUpdated(new Date());
        return source;
    }

    @Override
    protected UpstreamConsumerDTOArrayElement initDestinationObject() {
        return new UpstreamConsumerDTOArrayElement();
    }

    @Override
    protected void verifyOutput(UpstreamConsumer source, UpstreamConsumerDTOArrayElement dest,
        boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getCreated(), dest.getCreated() != null ?
                new Date(dest.getCreated().toInstant().toEpochMilli()) : null);
            assertEquals(source.getUpdated(), dest.getUpdated() != null ?
                new Date(dest.getUpdated().toInstant().toEpochMilli()) : null);

            if (childrenGenerated) {
                this.certificateTranslatorTest
                        .verifyOutput((Certificate) source.getIdCert(), dest.getIdCert(), true);
            }
            else {
                assertNull(dest.getIdCert());
            }
        }
    }
}
