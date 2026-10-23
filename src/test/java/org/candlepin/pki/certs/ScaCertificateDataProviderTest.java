/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ScaCertificateDataProviderTest {
    @Test
    void preparesOwnerPathWithoutEnvironments() {
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(new Owner().setId("owner").setKey("org"));
        ScaCertificateDataProvider provider = new ScaCertificateDataProvider(mock(EnvironmentCurator.class));

        ScaCertificateData data = provider.prepare(consumer, List.of());

        assertThat(data.subject().commonName()).isEqualTo("consumer");
        assertThat(data.subject().organizationName()).isEqualTo("org");
        assertThat(data.accessPaths()).containsExactly("/org");
    }

    @Test
    void snapshotsEncodedPathsInEnvironmentOrder() {
        Owner owner = new Owner().setId("owner").setKey("org//team");
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        Environment first = new Environment().setName("Release Candidate/$env");
        Environment second = new Environment().setName("//Second/ /Environment//");
        List<Environment> environments = new ArrayList<>(List.of(first, second));
        ScaCertificateDataProvider provider = new ScaCertificateDataProvider(mock(EnvironmentCurator.class));

        ScaCertificateData data = provider.prepare(consumer, environments);
        owner.setKey("changed");
        first.setName("changed");
        environments.clear();

        assertNotNull(data.accessPaths());
        assertThat(data.accessPaths()).containsExactly(
            "/org/team/Release+Candidate/%24env", "/org/team/Second/Environment");
        assertThat(data.subject().organizationName()).isEqualTo("org//team");
        assertThrows(UnsupportedOperationException.class, () -> data.accessPaths().add("/changed"));
    }

    @Test
    void preservesCuratorPriorityOrder() {
        Consumer consumer = new Consumer();
        List<Environment> environments = List.of(new Environment().setName("first"),
            new Environment().setName("second"));
        EnvironmentCurator curator = mock(EnvironmentCurator.class);
        when(curator.getConsumerEnvironments(consumer)).thenReturn(environments);

        assertThat(new ScaCertificateDataProvider(curator).getEnvironments(consumer)).isSameAs(environments);
    }

    @Test
    void rejectsMissingInput() {
        ScaCertificateDataProvider provider = new ScaCertificateDataProvider(mock(EnvironmentCurator.class));
        Consumer consumer = new Consumer().setOwner(new Owner().setId("owner").setKey("org"));
        Consumer emptyConsumer = new Consumer();

        assertThrows(NullPointerException.class, () -> provider.getEnvironments(null));
        assertThrows(NullPointerException.class, () -> provider.prepare(null, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(emptyConsumer, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(consumer, null));
    }
}
