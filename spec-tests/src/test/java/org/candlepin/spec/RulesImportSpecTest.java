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
package org.candlepin.spec;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@SpecTest
public class RulesImportSpecTest {

    @Test
    @ResourceLock("rules")
    public void shouldGetRules() throws Exception {
        ApiClient adminClient = ApiClients.admin();

        assertNotNull(adminClient.rules().listRules());
    }
}
