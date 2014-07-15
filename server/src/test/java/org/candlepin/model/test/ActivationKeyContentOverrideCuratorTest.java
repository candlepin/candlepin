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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ActivationKeyContentOverrideCuratorTest
 *
 * Slightly modified from ConsumerContentOverrideCuratorTest
 */
public class ActivationKeyContentOverrideCuratorTest extends DatabaseTestFixture {
    private Owner owner;
    private ConsumerType ct;
    private ActivationKey key;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        key = new ActivationKey("a key", owner);
        activationKeyCurator.create(key);
    }

    @Test
    public void normalCreateAndRetrieve() {
        ActivationKeyContentOverride cco = new ActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        ActivationKeyContentOverride cco2 = activationKeyContentOverrideCurator.retrieve(
            key, "test-content", "name");
        assert (cco2 != null);
        assertEquals("value", cco2.getValue());
    }

    @Test
    public void normalCreateAndUpdate() {
        ActivationKeyContentOverride cco = new ActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        cco.setValue("value-update");
        activationKeyContentOverrideCurator.merge(cco);

        ActivationKeyContentOverride cco2 =
                activationKeyContentOverrideCurator.retrieve(
                    key, "test-content", "name");
        assert (cco2 != null);
        assertEquals("value-update", cco2.getValue());
    }

    @Test
    public void deleteByName() {
        ActivationKeyContentOverride cco = new ActivationKeyContentOverride(
            key, "test-content", "name", "value");
        activationKeyContentOverrideCurator.create(cco);

        activationKeyContentOverrideCurator.removeByName(key, "test-content", "name");
        ActivationKeyContentOverride cco2 = activationKeyContentOverrideCurator.retrieve(
            key, "test-content", "name");
        assert (cco2 == null);
    }

    @Test
    public void deleteByLabel() {
        ActivationKeyContentOverride cco1 = new ActivationKeyContentOverride(
            key, "test-content", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = new ActivationKeyContentOverride(
            key, "test-content", "name2", "value");
        activationKeyContentOverrideCurator.create(cco2);

        activationKeyContentOverrideCurator.removeByContentLabel(key, "test-content");
        List<ActivationKeyContentOverride> ccoList =
            activationKeyContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }

    @Test
    public void deleteByConsumer() {
        ActivationKeyContentOverride cco1 = new ActivationKeyContentOverride(
            key, "test-content1", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = new ActivationKeyContentOverride(
            key, "test-content2", "name2", "value");
        activationKeyContentOverrideCurator.create(cco2);

        activationKeyContentOverrideCurator.removeByParent(key);
        List<ActivationKeyContentOverride> ccoList =
            activationKeyContentOverrideCurator.listAll();
        assertEquals(ccoList.size(), 0);
    }

    @Test
    public void testAddOrUpdateUpdatesValue() {
        ActivationKeyContentOverride cco1 = new ActivationKeyContentOverride(
            key, "test-content1", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = new ActivationKeyContentOverride(
            key, "test-content1", "name1", "value2");
        activationKeyContentOverrideCurator.addOrUpdate(key, cco2);

        List<ActivationKeyContentOverride> ccoList =
            activationKeyContentOverrideCurator.listAll();
        assertEquals(1, ccoList.size());
        assertEquals("value2", ccoList.get(0).getValue());
    }

    @Test
    public void testAddOrUpdateCreatesNew() {
        ActivationKeyContentOverride cco1 = new ActivationKeyContentOverride(
            key, "test-content1", "name1", "value");
        activationKeyContentOverrideCurator.create(cco1);
        ActivationKeyContentOverride cco2 = new ActivationKeyContentOverride(
            key, "test-content2", "name2", "value2");
        activationKeyContentOverrideCurator.addOrUpdate(key, cco2);

        List<ActivationKeyContentOverride> ccoList =
            activationKeyContentOverrideCurator.listAll();
        assertEquals(2, ccoList.size());
    }

    @Test
    public void testCreateOverride() {
        ActivationKeyContentOverride override = new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1");
        assertEquals(override, this.activationKeyContentOverrideCurator.create(override));
    }

    @Test
    public void testCreateOverrideForcesLowercaseName() {
        ActivationKeyContentOverride override = new ActivationKeyContentOverride(key,
            "test-repo", "GpGCheck", "1");
        ActivationKeyContentOverride created =
            this.activationKeyContentOverrideCurator.create(override);
        assertEquals("gpgcheck", created.getName());
    }

    @Test
    public void testModifyOverride() {
        ActivationKeyContentOverride override = new ActivationKeyContentOverride(key,
            "test-repo", "GpGCheck", "1");
        ActivationKeyContentOverride created =
            this.activationKeyContentOverrideCurator.create(override);
        created.setValue("0");
        ActivationKeyContentOverride merged =
            this.activationKeyContentOverrideCurator.merge(created);
        assertEquals("0", merged.getValue());
    }

    @Test
    public void testModifyOverrideForcesNameToLowercase() {
        ActivationKeyContentOverride override = new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "0");
        ActivationKeyContentOverride created =
            this.activationKeyContentOverrideCurator.create(override);
        created.setName("GPGCHECK");
        ActivationKeyContentOverride merged =
            this.activationKeyContentOverrideCurator.merge(created);
        assertEquals("gpgcheck", merged.getName());
    }

    @Test
    public void testRetrieveByName() {
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        ActivationKeyContentOverride found =
            activationKeyContentOverrideCurator.retrieve(key, "test-repo", "gpgcheck");
        assertNotNull(found);
        assertEquals(key, found.getKey());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameIsCaseInsensitive() {
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        ActivationKeyContentOverride found =
            activationKeyContentOverrideCurator.retrieve(key, "test-repo", "GPGCheck");
        assertNotNull(found);
        assertEquals(key, found.getKey());
        assertEquals("test-repo", found.getContentLabel());
        assertEquals("gpgcheck", found.getName());
        assertEquals("1", found.getValue());
    }

    @Test
    public void testRetrieveByNameDoesntExist() {
        ActivationKeyContentOverride found =
            activationKeyContentOverrideCurator.retrieve(key, "not-a-repo", "gpgcheck");
        assertNull(found);
    }

    @Test
    public void testRemoveByName() {
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "remaining-override", "remaining"));
        activationKeyContentOverrideCurator.removeByName(key, "test-repo", "gpgcheck");
        List<ActivationKeyContentOverride> remaining =
            activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByNameCaseInsensitive() {
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "remaining-override", "remaining"));
        activationKeyContentOverrideCurator.removeByName(key, "test-repo", "GpGChecK");
        List<ActivationKeyContentOverride> remaining =
            activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("remaining-override", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByContentLabel() {
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "foo", "foo-v"));
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "should-remain", "remaining", "true"));
        activationKeyContentOverrideCurator.removeByContentLabel(key, "test-repo");
        List<ActivationKeyContentOverride> remaining =
            activationKeyContentOverrideCurator.getList(key);
        assertEquals(1, remaining.size());
        assertEquals("should-remain", remaining.get(0).getContentLabel());
        assertEquals("remaining", remaining.get(0).getName());
    }

    @Test
    public void testRemoveByConsumer() {
        ActivationKey key2 = new ActivationKey("other key", owner);
        key2 = activationKeyCurator.create(key2);
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key2,
            "test-repo", "gpgcheck", "1"));

        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "test-repo", "gpgcheck", "1"));
        activationKeyContentOverrideCurator.create(new ActivationKeyContentOverride(key,
            "another-test-repo", "gpgcheck", "0"));
        activationKeyContentOverrideCurator.removeByParent(key);

        assertTrue(activationKeyContentOverrideCurator.getList(key).isEmpty());
        assertEquals(1, activationKeyContentOverrideCurator.getList(key2).size());
    }
}
