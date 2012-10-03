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
import static org.junit.Assert.assertNotNull;

import javax.persistence.PersistenceException;

import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;


public class EnvironmentContentCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Environment e;
    private Product p;
    private Content c;
    private EnvironmentContent envContent;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);

        e = new Environment("env1", "Env 1", owner);
        envCurator.create(e);

        p = TestUtil.createProduct();
        c = new Content("testcontent", "contentId1", "testcontent",
            "yum", "red hat", "http://example.com", "http://example.com/gpg.key");
        contentCurator.create(c);
        p.addContent(c);
        productCurator.create(p);

        envContent = new EnvironmentContent(e, c.getId(), true);
        envContent = envContentCurator.create(envContent);
    }

    @Test
    public void create() {
        envContent = envContentCurator.lookupByEnvironmentAndContent(e, c.getId());
        assertNotNull(envContent);

        e = envCurator.find(e.getId());
        assertEquals(1, e.getEnvironmentContent().size());

        assertEquals(1, envContentCurator.lookupByContent(c.getId()).size());
    }

    @Test
    public void deleteEnvCleansUpPromotedContent() {
        assertEquals(1, envContentCurator.listAll().size());
        envCurator.delete(e);
        assertEquals(0, envContentCurator.listAll().size());
    }

    @Test(expected = PersistenceException.class)
    public void createDuplicate() {
        envContent = new EnvironmentContent(e, c.getId(), true);
        envContentCurator.create(envContent);
    }

    @Test
    public void delete() {
        assertEquals(1, envContentCurator.listAll().size());
        e.getEnvironmentContent().remove(envContent); // TODO
        envContentCurator.delete(envContent);
        assertEquals(0, envContentCurator.listAll().size());
    }



}
