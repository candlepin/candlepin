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
import org.candlepin.dto.DTOFactory;
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

    protected CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();
    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();

    @Override
    protected void initFactory(DTOFactory factory) {
        // Note that the UpstreamConsumerTranslator instance here won't be the same as the one
        // returned by initTranslator. At the time of writing, this isn't important (as it's
        // stateless), but if that detail becomes significant in the future, this will need to
        // change.
        this.certificateTranslatorTest.initFactory(factory);
        this.consumerTypeTranslatorTest.initFactory(factory);
        factory.registerTranslator(UpstreamConsumer.class, new UpstreamConsumerTranslator());
    }

    @Override
    protected UpstreamConsumerTranslator initTranslator() {
        return new UpstreamConsumerTranslator();
    }

    @Override
    protected UpstreamConsumer initSourceEntity() {
        UpstreamConsumer consumer = new UpstreamConsumer();

        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setApiUrl("http://www.url.com");
        consumer.setWebUrl("http://www.url.com");
        consumer.setOwnerId("owner_id");
        consumer.setType(this.consumerTypeTranslatorTest.initSourceEntity());
        consumer.setIdCert((IdentityCertificate) this.certificateTranslatorTest.initSourceEntity());

        return consumer;
    }

    @Override
    protected UpstreamConsumerDTO initDestDTO() {
        // Nothing fancy to do here.
        return new UpstreamConsumerDTO();
    }

    @Override
    protected void verifyDTO(UpstreamConsumer source, UpstreamConsumerDTO dto, boolean childrenGenerated) {
        if (source != null) {
            UpstreamConsumer src = (UpstreamConsumer) source;
            UpstreamConsumerDTO dest = (UpstreamConsumerDTO) dto;

            assertEquals(src.getId(), dest.getId());
            assertEquals(src.getUuid(), dest.getUuid());
            assertEquals(src.getName(), dest.getName());
            assertEquals(src.getApiUrl(), dest.getApiUrl());
            assertEquals(src.getWebUrl(), dest.getWebUrl());
            assertEquals(src.getOwnerId(), dest.getOwnerId());

            if (childrenGenerated) {
                this.certificateTranslatorTest
                    .verifyDTO((Certificate) src.getIdCert(), dest.getIdentityCertificate(), true);

                this.consumerTypeTranslatorTest
                    .verifyDTO(src.getType(), dest.getConsumerType(), true);
            }
            else {
                assertNull(dest.getConsumerType());
                assertNull(dest.getIdentityCertificate());
            }
        }
        else {
            assertNull(dto);
        }
    }
}
