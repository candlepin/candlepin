/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.guice.CandlepinCapabilities;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

@ExtendWith(MockitoExtension.class)
public class V3CapabilityCheckTest {
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;

    @Test
    void shouldRequireAConsumer() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);

        assertThatThrownBy(() -> v3CapabilityCheck.isCertV3Capable(null))
            .isInstanceOf(IllegalArgumentException.class);

        Consumer consumer = new Consumer();
        assertThatThrownBy(() -> v3CapabilityCheck.isCertV3Capable(consumer))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manifestWithCapabilityShouldBeCapable() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);
        ConsumerType type = createConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        when(this.consumerTypeCurator.getConsumerType(any(Consumer.class))).thenReturn(type);
        Consumer consumer = new Consumer().setType(type)
            .setCapabilities(defaultCapabilities());

        boolean certV3Capable = v3CapabilityCheck.isCertV3Capable(consumer);

        assertThat(certV3Capable).isTrue();
    }

    @Test
    void manifestWithoutCapabilityShouldNotBeCapable() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);
        ConsumerType type = createConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        when(this.consumerTypeCurator.getConsumerType(any(Consumer.class))).thenReturn(type);
        Consumer consumer = new Consumer().setType(type);

        boolean certV3Capable = v3CapabilityCheck.isCertV3Capable(consumer);

        assertThat(certV3Capable).isFalse();
    }

    @Test
    void hypervisorShouldBeCapable() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);
        ConsumerType type = createConsumerType(ConsumerType.ConsumerTypeEnum.HYPERVISOR);
        when(this.consumerTypeCurator.getConsumerType(any(Consumer.class))).thenReturn(type);
        Consumer consumer = new Consumer().setType(type);

        boolean certV3Capable = v3CapabilityCheck.isCertV3Capable(consumer);

        assertThat(certV3Capable).isTrue();
    }

    @Test
    void ordinaryConsumerWithFactShouldBeCapable() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);
        ConsumerType type = createConsumerType(ConsumerType.ConsumerTypeEnum.PERSON);
        when(this.consumerTypeCurator.getConsumerType(any(Consumer.class))).thenReturn(type);
        Consumer consumer = new Consumer().setType(type)
            .setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, "3.3");

        boolean certV3Capable = v3CapabilityCheck.isCertV3Capable(consumer);

        assertThat(certV3Capable).isTrue();
    }

    @Test
    void ordinaryConsumerWithoutFactShouldNotBeCapable() {
        V3CapabilityCheck v3CapabilityCheck = new V3CapabilityCheck(this.consumerTypeCurator);
        ConsumerType type = createConsumerType(ConsumerType.ConsumerTypeEnum.PERSON);
        when(this.consumerTypeCurator.getConsumerType(any(Consumer.class))).thenReturn(type);
        Consumer consumer = new Consumer().setType(type);

        boolean certV3Capable = v3CapabilityCheck.isCertV3Capable(consumer);

        assertThat(certV3Capable).isFalse();
    }

    private ConsumerType createConsumerType(ConsumerType.ConsumerTypeEnum type) {
        return new ConsumerType(type)
            .setId(TestUtil.randomString("type"));
    }

    private Collection<ConsumerCapability> defaultCapabilities() {
        return CandlepinCapabilities.getCapabilities()
            .stream()
            .map(ConsumerCapability::new)
            .toList();
    }
}
