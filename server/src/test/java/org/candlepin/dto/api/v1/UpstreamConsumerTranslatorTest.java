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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Certificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.UpstreamConsumer;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;



/**
 * Test suite for the UpstreamConsumerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class UpstreamConsumerTranslatorTest extends
    AbstractTranslatorTest<UpstreamConsumer, UpstreamConsumerDTO, UpstreamConsumerTranslator> {

    protected UpstreamConsumerTranslator translator = new UpstreamConsumerTranslator();

    protected CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();
    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();


    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(
            this.translator, UpstreamConsumer.class, UpstreamConsumerDTO.class);
    }

    @Override
    protected UpstreamConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected UpstreamConsumer initSourceObject() {
        UpstreamConsumer consumer = new UpstreamConsumer();

        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setApiUrl("http://www.url.com");
        consumer.setWebUrl("http://www.url.com");
        consumer.setOwnerId("owner_id");
        consumer.setType(this.consumerTypeTranslatorTest.initSourceObject());
        consumer.setIdCert((IdentityCertificate) this.certificateTranslatorTest.initSourceObject());

        return consumer;
    }

    @Override
    protected UpstreamConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new UpstreamConsumerDTO();
    }

    @Override
    protected void verifyOutput(UpstreamConsumer source, UpstreamConsumerDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getApiUrl(), dest.getApiUrl());
            assertEquals(source.getWebUrl(), dest.getWebUrl());
            assertEquals(source.getOwnerId(), dest.getOwnerId());

            if (childrenGenerated) {
                this.certificateTranslatorTest
                    .verifyOutput((Certificate) source.getIdCert(), dest.getIdentityCertificate(), true);

                this.consumerTypeTranslatorTest
                    .verifyOutput(source.getType(), dest.getConsumerType(), true);
            }
            else {
                assertNull(dest.getConsumerType());
                assertNull(dest.getIdentityCertificate());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
