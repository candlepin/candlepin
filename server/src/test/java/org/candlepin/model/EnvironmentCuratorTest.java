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
package org.candlepin.model;

import static org.junit.Assert.*;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;



public class EnvironmentCuratorTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ContentCurator contentCurator;
    @Inject private EnvironmentContentCurator envContentCurator;
    @Inject private EnvironmentCurator envCurator;

    private Owner owner;
    private Environment environment;

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner("test-owner", "Test Owner"));
        environment = envCurator.create(new Environment("env1", "Env 1", owner));
    }

    @Test
    public void create() {
        assertEquals(1, envCurator.listAll().list().size());
        Environment e = envCurator.find("env1");
        assertEquals(owner, e.getOwner());
    }

    @Test public void delete() {
        envCurator.delete(environment);
        assertEquals(0, envCurator.listAll().list().size());
    }

    @Test public void listForOwner() {
        List<Environment> envs = envCurator.listForOwner(owner).list();
        assertEquals(1, envs.size());
        assertEquals(envs.get(0), environment);
    }

    @Test public void listForOwnerByName() {
        Environment e = envCurator.create(new Environment("env2", "Another Env", owner));

        List<Environment> envs = envCurator.listForOwnerByName(owner, "Another Env").list();
        assertEquals(1, envs.size());
        assertEquals(e, envs.get(0));
    }

    @Test
    public void listWithContent() {
        envCurator.create(new Environment("env2", "Another Env", owner));

        final String contentId = "contentId";
        Content content = this.createContent(contentId, "test content", this.owner);

        envContentCurator.create(new EnvironmentContent(environment, content, true));

        Set<String> ids = new HashSet<String>();
        ids.add(contentId);

        List<Environment> envs = envCurator.listWithContent(ids);

        assertEquals(1, envs.size());
        assertEquals(environment, envs.get(0));
    }

    @Test
    public void testDeleteEnvironmentForOwner() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Content content1 = this.createContent("c1", "c1", owner1);
        Content content2 = this.createContent("c2", "c2", owner1);
        Content content3 = this.createContent("c3", "c3", owner2);

        Environment environment1 = this.createEnvironment(
            owner1, "test_env-1", "test_env-1", null, null, Arrays.asList(content1)
        );

        Environment environment2 = this.createEnvironment(
            owner1, "test_env-2", "test_env-2", null, null, Arrays.asList(content2)
        );

        Environment environment3 = this.createEnvironment(
            owner2, "test_env-3", "test_env-3", null, null, Arrays.asList(content3)
        );

        int output = this.environmentCurator.deleteEnvironmentsForOwner(owner1);
        assertEquals(2, output);

        this.environmentCurator.evict(environment1);
        this.environmentCurator.evict(environment2);
        this.environmentCurator.evict(environment3);
        environment1 = this.environmentCurator.find(environment1.getId());
        environment2 = this.environmentCurator.find(environment2.getId());
        environment3 = this.environmentCurator.find(environment3.getId());

        assertNull(environment1);
        assertNull(environment2);
        assertNotNull(environment3);

        assertEquals(1, environment3.getEnvironmentContent().size());
        assertEquals(content3.getUuid(), environment3.getEnvironmentContent().iterator().next().getContent()
            .getUuid());
    }
}
