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

import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.fedoraproject.candlepin.auth.NoAuthPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.resource.ActivateResource;
import org.fedoraproject.candlepin.resource.ActivationKeyResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * SubscriptionTokenResourceTest
 */
public class ActivateResourceTest extends DatabaseTestFixture {
    protected ActivateResource activateResource;
    protected ActivationKeyResource activationKeyResource;

    @Before
    public void setUp() {

        activateResource = injector
            .getInstance(ActivateResource.class);
        activationKeyResource = injector
        .getInstance(ActivationKeyResource.class);
    }

//    @Test
//    public void testCustomerCreateWithNoStringFails() {
//        ActivationKey key = new ActivationKey();
//        Owner owner = createOwner();
//        key.setOwner(owner);
//        key.setName("dd");
//        key = activationKeyResource.createActivationKey(key);
//        Consumer con = new Consumer();
//        con.setName("test");
//        Principal principal = new NoAuthPrincipal();
//        ArrayList<String> keys = new ArrayList<String>();
//        try {
//            activateResource.activate(con, principal, keys);
//        }
//        catch (BadRequestException e) {
//            // expected, lets try null
//            try {
//                activateResource.activate(con, principal, null);
//            }
//            catch (BadRequestException ee) {
//                // expected, lets try null
//                return;
//            }
//        }
//        fail("No excpetion was thrown");
//    }

    //FIXME wait for ownergeddon to work
    /*
    @Test
    public void testCustomerCreateWithOneKeyWorks() {
        ActivationKey key = new ActivationKey();
        ConsumerType system = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(system);
        Owner owner = createOwner();
        key.setOwner(owner);
        key.setName("dd");
        key = activationKeyResource.createActivationKey(key);
        Consumer con = new Consumer();
        con.setName("test");
        con.setType(system);
        Principal principal = new NoAuthPrincipal();
        ArrayList<String> keys = new ArrayList<String>();
        keys.add(key.getId());
        con = activateResource.activate(con, principal, "test", keys);
        assertEquals(owner.getId(), con.getOwner().getId());
        fail("No excpetion was thrown");
    }*/
}
