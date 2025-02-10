/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Date;

public class ContentAccessPayloadCuratorTest extends DatabaseTestFixture {

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetContentAccessPayloadWithInvalidOwnerId(String ownerId) {
        ContentAccessPayload actual = this.caPayloadCurator
            .getContentAccessPayload(ownerId, "payloadKey");

        assertNull(actual);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetContentAccessPayloadWithInvalidPayloadKey(String payloadKey) {
        ContentAccessPayload actual = this.caPayloadCurator
            .getContentAccessPayload("ownerId", payloadKey);

        assertNull(actual);
    }

    @Test
    public void testGetContentAccessPayload() {
        Owner owner = this.createOwner();

        ContentAccessPayload payload1 = new ContentAccessPayload()
            .setOwner(owner)
            .setPayload("ca-payload-1")
            .setPayloadKey("ca-payload-1-key")
            .setTimestamp(new Date());

        ContentAccessPayload payload2 = new ContentAccessPayload()
            .setOwner(owner)
            .setPayload("ca-payload-2")
            .setPayloadKey("ca-payload-2-key")
            .setTimestamp(new Date());

        this.caPayloadCurator.create(payload1);
        this.caPayloadCurator.create(payload2);

        ContentAccessPayload actual = this.caPayloadCurator
            .getContentAccessPayload(owner.getId(), payload1.getPayloadKey());

        assertThat(actual)
            .isNotNull()
            .isEqualTo(payload1);
    }

    @Test
    public void testGetContentAccessPayloadWithUnknownOwnerId() {
        Owner owner = this.createOwner();

        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwner(owner)
            .setPayload("ca-payload")
            .setPayloadKey("ca-payload-key")
            .setTimestamp(new Date());

        this.caPayloadCurator.create(payload);

        ContentAccessPayload actual = this.caPayloadCurator
            .getContentAccessPayload("unknown", payload.getPayloadKey());

        assertNull(actual);
    }

    @Test
    public void testGetContentAccessPayloadWithUnknownPayloadKey() {
        Owner owner = this.createOwner();

        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwner(owner)
            .setPayload("ca-payload")
            .setPayloadKey("ca-payload-key")
            .setTimestamp(new Date());

        this.caPayloadCurator.create(payload);

        ContentAccessPayload actual = this.caPayloadCurator
            .getContentAccessPayload(owner.getId(), "unknown-payload-key");

        assertNull(actual);
    }

}
