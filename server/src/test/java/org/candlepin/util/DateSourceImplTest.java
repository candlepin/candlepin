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
package org.candlepin.util;

import java.util.Date;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Test Class for the DateSourceImpl class
 */
public class DateSourceImplTest {

    protected DateSourceImpl impl = new DateSourceImpl();

    @Test
    public void testGetDate() {
        long milis = System.currentTimeMillis() - 1000;
        Date dt = new Date(milis);
        assertTrue(dt.before(impl.currentDate()));
    }
}
