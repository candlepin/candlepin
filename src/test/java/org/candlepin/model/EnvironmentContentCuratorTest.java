/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.PersistenceException;



public class EnvironmentContentCuratorTest extends DatabaseTestFixture {
    private Owner owner;
    private Environment e;
    private Product p;
    private Content c;
    private EnvironmentContent envContent;

    @BeforeEach
    public void setUp() throws Exception {
        owner = this.createOwner("test-owner", "Test Owner");

        e = this.createEnvironment(owner, "env1", "Env 1");
        c = this.createContent("contentId1", "testcontent");

        p = TestUtil.createProduct();
        p.addContent(c, true);
        p = this.createProduct(p);

        envContent = new EnvironmentContent()
            .setEnvironment(e)
            .setContent(c)
            .setEnabled(true);

        envContent = environmentContentCurator.create(envContent);
        environmentContentCurator.flush();
        environmentContentCurator.clear();
    }

    @Test
    public void create() {
        envContent = environmentContentCurator.getByEnvironmentAndContent(e, c.getId());
        assertNotNull(envContent);

        e = environmentCurator.get(e.getId());
        assertEquals(1, e.getEnvironmentContent().size());

        assertEquals(1, environmentContentCurator.getByContent(owner, c.getId()).size());
    }

    @Test
    public void deleteEnvCleansUpPromotedContent() {
        assertEquals(1, environmentContentCurator.listAll().size());
        environmentCurator.delete(e);
        assertEquals(0, environmentContentCurator.listAll().size());
    }

    @Test
    public void createDuplicate() {
        envContent = new EnvironmentContent()
            .setEnvironment(e)
            .setContent(c)
            .setEnabled(true);

        assertThrows(PersistenceException.class, () -> environmentContentCurator.create(envContent));
    }

    @Test
    public void delete() {
        assertEquals(1, environmentContentCurator.listAll().size());
        e.getEnvironmentContent().remove(envContent); // TODO
        environmentContentCurator.delete(envContent);
        assertEquals(0, environmentContentCurator.listAll().size());
    }

}
