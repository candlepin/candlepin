/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.sync;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;

import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.junit.Test;

/**
 * RulesExporterTest
 */
public class RulesExporterTest {

    private String FAKE_RULES = "HELLO WORLD";

    @Test
    public void testMetaExporter() throws IOException {
        RulesCurator rulesCurator = mock(RulesCurator.class);
        when(rulesCurator.getRules()).thenReturn(new Rules(FAKE_RULES));
        RulesExporter exporter = new RulesExporter(rulesCurator);
        StringWriter writer = new StringWriter();
        exporter.export(writer);
        assertEquals(FAKE_RULES, writer.toString());
    }

}
