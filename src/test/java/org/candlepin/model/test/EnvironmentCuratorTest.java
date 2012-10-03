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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;


public class EnvironmentCuratorTest extends DatabaseTestFixture {
    private Owner owner;
    private Environment environment;

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner("test-owner", "Test Owner"));
        environment = envCurator.create(new Environment("env1", "Env 1", owner));
    }

    @Test
    public void create() {
        assertEquals(1, envCurator.listAll().size());
        Environment e = envCurator.find("env1");
        assertEquals(owner, e.getOwner());
    }

    @Test public void delete() {
        envCurator.delete(environment);
        assertEquals(0, envCurator.listAll().size());
    }

    @Test public void listForOwner() {
        List<Environment> envs = envCurator.listForOwner(owner);
        assertEquals(1, envs.size());
        assertEquals(envs.get(0), environment);
    }

    @Test public void listForOwnerByName() {
        Environment e = envCurator.create(new Environment("env2", "Another Env", owner));

        List<Environment> envs = envCurator.listForOwnerByName(owner, "Another Env");
        assertEquals(1, envs.size());
        assertEquals(e, envs.get(0));
    }

    @Test
    public void listWithContent() {
        Environment e = envCurator.create(new Environment("env2", "Another Env", owner));

        final String contentId = "contentId";
        EnvironmentContent ec = envContentCurator.create(
            new EnvironmentContent(environment, contentId, true));

        Set<String> ids = new HashSet<String>();
        ids.add(contentId);

        List<Environment> envs = envCurator.listWithContent(ids);

        assertEquals(1, envs.size());
        assertEquals(environment, envs.get(0));
    }
}
