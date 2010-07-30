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
package org.fedoraproject.candlepin.resource.test;

import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.resource.SubscriptionTokenResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * SubscriptionTokenResourceTest
 */
public class SubscriptionTokenResourceTest extends DatabaseTestFixture {
    protected SubscriptionTokenResource subTokenResource;

    @Before
    public void setUp() {

        subTokenResource = injector
            .getInstance(SubscriptionTokenResource.class);
    }

    @Test
    public void testInvalidTokenIdOnDelete() {
        try {
            subTokenResource.deleteSubscription("JarJarBinks");
        }
        catch (BadRequestException e) {
            // this is expected.
        }

    }

}
