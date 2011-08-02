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

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.resource.RulesResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;
import org.apache.commons.codec.binary.Base64;

/**
 * RulesResourceTest
 */
public class RulesResourceTest extends DatabaseTestFixture {

    private RulesResource rulesResource;

    @Before
    public void setUp() {

        rulesResource = injector.getInstance(RulesResource.class);
    }

    @Test
    public void testUpload() {
        String rulesBuffer = new String(Base64.encodeBase64String(("//foobar"
            .getBytes())));
        rulesResource.upload(rulesBuffer);
    }

    @Test
    public void testGet() {

        String rulesBuffer = new String(Base64.encodeBase64String(("//foobar"
            .getBytes())));
        rulesResource.upload(rulesBuffer);
        String rules = rulesResource.get();
        assertEquals(rules, rulesBuffer);
    }

    @Test
    public void testDelete() {
        String origRules = rulesResource.get();
        String newRules = new String(Base64.encodeBase64String(("//foobar"
            .getBytes())));
        rulesResource.upload(newRules);
        rulesResource.delete();
        String rulesAfterDelete = rulesResource.get();
        assertEquals(rulesAfterDelete, origRules);
    }
}
