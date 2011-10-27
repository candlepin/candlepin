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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.StringReader;

import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * RulesImporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class RulesImporterTest {

    @Mock private RulesCurator curator;
    private RulesImporter importer;
    private String RULE = "good bye, cruel world!";

    @Before
    public void setUp() {
        importer = new RulesImporter(curator);
    }

    @Test
    public void importRules() throws IOException {
        importer.importObject(new StringReader(RULE));
        verify(curator).update(any(Rules.class)); // TODO: can't get custom matcher to work?
    }

    static class RulesMatcher extends ArgumentMatcher<Rules> {
        private String rule;

        public RulesMatcher(String rule) {
            this.rule = rule;
        }

        public boolean matches(Object rules) {
            return ((Rules) rules).getRules().equals(rule);
        }
    }
}
