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

package org.candlepin.gutterball.curator;

import static org.junit.Assert.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.model.ConsumerState;

import org.junit.Test;

import java.util.Date;

public class ConsumerStateCuratorTest extends DatabaseTestFixture {

    @Test
    public void testInsertAndFindByUUID() {
        ConsumerState state = new ConsumerState("abc-123", "test-owner", new Date());
        consumerStateCurator.create(state);

        ConsumerState found = consumerStateCurator.findByUuid("abc-123");
        assertNotNull(found);
        assertEquals(state.getUuid(), found.getUuid());
    }

    @Test
    public void testSetConsumerDeleted() {
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", new Date());
        consumerStateCurator.create(state);

        Date deletedOn = new Date();
        consumerStateCurator.setConsumerDeleted(state.getUuid(), deletedOn);

        ConsumerState found = consumerStateCurator.findByUuid(uuid);
        assertEquals(deletedOn, found.getDeleted());
    }

}
