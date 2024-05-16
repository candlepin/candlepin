/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;
import java.util.Set;

public class DistributorVersionCuratorTest extends DatabaseTestFixture {

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindByNameWithInvalidName(String name) {
        DistributorVersion actual = distributorVersionCurator.findByName(name);

        assertNull(actual);
    }

    @Test
    public void testFindByNameWithNoExistingDistributorVersion() {
        DistributorVersion version = new DistributorVersion();
        version.setDisplayName(TestUtil.randomString());
        version.setName(TestUtil.randomString());
        distributorVersionCurator.create(version);

        DistributorVersion actual = distributorVersionCurator.findByName(TestUtil.randomString());

        assertNull(actual);
    }

    @Test
    public void testFindByNameWithExistingDistributorVersion() {
        DistributorVersion version1 = new DistributorVersion();
        version1.setDisplayName(TestUtil.randomString());
        version1.setName(TestUtil.randomString());
        version1 = distributorVersionCurator.create(version1);

        DistributorVersion version2 = new DistributorVersion();
        version2.setDisplayName(TestUtil.randomString());
        version2.setName(TestUtil.randomString());
        distributorVersionCurator.create(version2);

        DistributorVersion actual = distributorVersionCurator.findByName(version1.getName());

        assertThat(actual)
            .isNotNull()
            .isEqualTo(version1);
    }

    @Test
    public void testFindByNameSearchWithNullName() {
        List<DistributorVersion> actual = distributorVersionCurator.findByNameSearch(null);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testFindByNameSearch() {
        String name = TestUtil.randomString();
        DistributorVersion version1 = new DistributorVersion();
        version1.setDisplayName(TestUtil.randomString());
        version1.setName(name);
        version1 = distributorVersionCurator.create(version1);

        String substringOfName = name.substring(1, name.length() - 1);
        DistributorVersion version2 = new DistributorVersion();
        version2.setDisplayName(TestUtil.randomString());
        version2.setName(substringOfName);
        version2 = distributorVersionCurator.create(version2);

        DistributorVersion version3 = new DistributorVersion();
        version3.setDisplayName(TestUtil.randomString());
        version3.setName(TestUtil.randomString());
        distributorVersionCurator.create(version3);

        List<DistributorVersion> actual = distributorVersionCurator
            .findByNameSearch(substringOfName);

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrder(version1, version2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindByCapabilityWithInvalidCapabilityName(String name) {
        List<DistributorVersion> actual = distributorVersionCurator.findByCapability(name);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testFindByCapability() {
        DistributorVersionCapability capability1 = new DistributorVersionCapability();
        capability1.setName(TestUtil.randomString());

        DistributorVersion version1 = new DistributorVersion();
        version1.setDisplayName(TestUtil.randomString());
        version1.setName(TestUtil.randomString());
        version1.setCapabilities(Set.of(capability1));
        version1 = distributorVersionCurator.create(version1);

        DistributorVersionCapability capability2 = new DistributorVersionCapability();
        capability2.setName(TestUtil.randomString());

        DistributorVersion version2 = new DistributorVersion();
        version2.setDisplayName(TestUtil.randomString());
        version2.setName(TestUtil.randomString());
        version2.setCapabilities(Set.of(capability2));
        distributorVersionCurator.create(version2);

        List<DistributorVersion> actual = distributorVersionCurator
            .findByCapability(capability1.getName());

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(version1);
    }
}
