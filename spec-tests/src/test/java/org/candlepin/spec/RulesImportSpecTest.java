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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.regex.Pattern;

@SpecTest
public class RulesImportSpecTest {
    @AfterEach
    public void afterEach() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        adminClient.rules().deleteRules();
        // The status resource response is being cached for 5 seconds
        Thread.sleep(7000);
    }

    @Test
    @ResourceLock("rules")
    public void shouldGetRules() throws Exception {
        ApiClient adminClient = ApiClients.admin();

        assertNotNull(adminClient.rules().listRules());
    }

    @Test
    @ResourceLock("rules")
    public void shouldPostAndGetRules() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        String originalRules = adminClient.rules().listRules();
        String version = getVersionFromRules(originalRules);
        assertNotNull(version);
        int majorVersion = Integer.parseInt(version.split(Pattern.quote("."))[0]);
        int minorVersion = Integer.parseInt(version.split(Pattern.quote("."))[1]);
        minorVersion++;
        String updatedRules = updateVersion(majorVersion, minorVersion, originalRules);

        adminClient.rules().uploadRules(updatedRules);

        String actual = adminClient.rules().listRules();
        assertThat(getVersionFromRules(actual))
            .isNotNull()
            .isEqualTo(majorVersion + "." + minorVersion);
    }

    @Test
    @ResourceLock("rules")
    public void shouldDeleteRules() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        String originalRules = adminClient.rules().listRules();
        String originalVersion = getVersionFromRules(originalRules);
        assertNotNull(originalVersion);
        int originalMajorVersion = Integer.parseInt(originalVersion.split(Pattern.quote("."))[0]);
        int originalMinorVersion = Integer.parseInt(originalVersion.split(Pattern.quote("."))[1]);
        updateVersion(originalMajorVersion, originalMinorVersion + 1, originalRules);

        adminClient.rules().deleteRules();

        // Version should be back to original:
        String actualRules = adminClient.rules().listRules();
        String actualVersion = getVersionFromRules(actualRules);
        assertThat(actualVersion)
            .isEqualTo(originalVersion);
    }

    private String getVersionFromRules(String rules) {
        String firstLine = rules.split("\\R")[0];
        firstLine = firstLine.replace("// Version:", "");
        firstLine = firstLine.replaceAll(" ", "");

        return firstLine;
    }

    private String updateVersion(int majorVersion, int minorVersion, String rules) {
        String[] lines = rules.split("\\R");
        lines[0] = "// Version: " + majorVersion + "." + minorVersion;

        return arrayOfStringsToString(lines);
    }

    private String arrayOfStringsToString(String[] array) {
        StringBuilder builder = new StringBuilder();
        for (String subString : array) {
            builder.append(subString + '\n');
        }

        return builder.toString();
    }
}
