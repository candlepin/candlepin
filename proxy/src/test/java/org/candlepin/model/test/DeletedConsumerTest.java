/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.candlepin.model.DeletedConsumer;
import org.junit.Test;

/**
 * DeletedConsumerTest
 */
public class DeletedConsumerTest {

    private DeletedConsumer dc = new DeletedConsumer("abcde", "10");

    @Test
    public void consumerId() {
        assertEquals("abcde", dc.getConsumerUuid());
        dc.setConsumerUuid("changed");
        assertEquals("changed", dc.getConsumerUuid());
    }

    @Test
    public void ownerId() {
        assertEquals("10", dc.getOwnerId());
        dc.setOwnerId("11");
        assertEquals("11", dc.getOwnerId());
    }

}
