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
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.persistence.PersistenceException;


public class EnvironmentContentCuratorTest extends DatabaseTestFixture {
    private Owner owner;
    private Environment e;
    private Product p;
    private Content c;
    private EnvironmentContent envContent;

    @Before
    public void setUp() {
        owner = this.createOwner("test-owner", "Test Owner");

        e = new Environment("env1", "Env 1", owner);
        environmentCurator.create(e);

        p = TestUtil.createProduct();
        c = new Content(this.owner, "testcontent", "contentId1", "testcontent", "yum",
            "red hat", "http://example.com", "http://example.com/gpg.key", "test-arch");
        contentCurator.create(c);
        p.addContent(c);
        p = this.createProduct(p, owner);

        envContent = new EnvironmentContent(e, c, true);
        envContent = environmentContentCurator.create(envContent);
    }

    @Test
    public void create() {
        envContent = environmentContentCurator.lookupByEnvironmentAndContent(e, c.getId());
        assertNotNull(envContent);

        e = environmentCurator.find(e.getId());
        assertEquals(1, e.getEnvironmentContent().size());

        assertEquals(1, environmentContentCurator.lookupByContent(owner, c.getId()).size());
    }

    @Test
    public void deleteEnvCleansUpPromotedContent() {
        assertEquals(1, environmentContentCurator.listAll().size());
        environmentCurator.delete(e);
        assertEquals(0, environmentContentCurator.listAll().size());
    }

    @Test(expected = PersistenceException.class)
    public void createDuplicate() {
        envContent = new EnvironmentContent(e, c, true);
        environmentContentCurator.create(envContent);
    }

    @Test
    public void delete() {
        assertEquals(1, environmentContentCurator.listAll().size());
        e.getEnvironmentContent().remove(envContent); // TODO
        environmentContentCurator.delete(envContent);
        assertEquals(0, environmentContentCurator.listAll().size());
    }



}
