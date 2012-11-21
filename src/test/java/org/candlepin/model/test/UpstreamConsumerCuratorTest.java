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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang.RandomStringUtils;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Owner;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.UpstreamConsumerCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

import java.util.List;

/**
 * UpstreamConsumerCuratorTest
 */
public class UpstreamConsumerCuratorTest extends DatabaseTestFixture {

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        UpstreamConsumer uc = new UpstreamConsumer("upstream name", owner, ct);
        uc = upstreamConsumerCurator.create(uc);

        List<UpstreamConsumer> results = entityManager().createQuery(
            "select c from UpstreamConsumer as c").getResultList();
        assertEquals(1, results.size());
        UpstreamConsumer c = results.get(0);
        assertEquals("upstream name", c.getName());
        assertEquals(uc.getId(), c.getId());
        assertEquals(uc.getUuid(), c.getUuid());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void delete() {
        // precondition
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        UpstreamConsumer uc = new UpstreamConsumer("upstream name", owner, ct);
        uc = upstreamConsumerCurator.create(uc);

        // test delete
        upstreamConsumerCurator.delete(uc);

        // verify
        List<UpstreamConsumer> results = entityManager().createQuery(
            "select c from UpstreamConsumer as c").getResultList();
        assertEquals(0, results.size());
    }

    @Test(expected = BadRequestException.class)
    public void validateForNameLength() {
        String longstr = RandomStringUtils.random(
            UpstreamConsumerCurator.NAME_LENGTH + 1);
        UpstreamConsumer uc = new UpstreamConsumer(longstr, null, null);
        upstreamConsumerCurator.create(uc);
    }

}
