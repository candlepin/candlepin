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

import java.util.List;

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;


public class EnvironmentCuratorTest extends DatabaseTestFixture {

    @Test
    public void create() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);

        Environment e = new Environment("env1", "Env 1", owner);
        envCurator.create(e);

        assertEquals(1, envCurator.listAll().size());
        e = envCurator.find("env1");
        assertEquals(owner, e.getOwner());
    }

    @Test public void delete() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);

        Environment e = new Environment("env1", "Env 1", owner);
        e = envCurator.create(e);

        envCurator.delete(e);
        assertEquals(0, envCurator.listAll().size());
    }

    @Test public void listForOwner() {

        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);

        Environment e = new Environment("env1", "Env 1", owner);
        e = envCurator.create(e);

        List<Environment> envs = envCurator.listForOwner(owner);
        assertEquals(1, envs.size());
    }

}
