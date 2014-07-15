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
package org.candlepin.sync;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.util.VersionUtil;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

/**
 * RulesImporter
 */
public class RulesImporter {
    private static Logger log = LoggerFactory.getLogger(RulesImporter.class);

    private RulesCurator curator;
    private EventSink sink;

    @Inject
    RulesImporter(RulesCurator curator, EventSink sink) {
        this.curator = curator;
        this.sink = sink;
    }

    public void importObject(Reader reader) throws IOException {
        Rules existingRules = curator.getRules();
        Rules newRules = new Rules(StringFromReader.asString(reader));

        // Only import if rules version is greater than what we currently have now:
        if (VersionUtil.getRulesVersionCompatibility(existingRules.getVersion(),
            newRules.getVersion())) {
            log.info("Importing new rules from manifest, current version: " +
                existingRules.getVersion() + " new version: " + newRules.getVersion());
            curator.update(newRules);
            sink.emitRulesModified(existingRules, newRules);
        }
        else {
            log.info("Ignoring older rules in manifest, current version: " +
                existingRules.getVersion() + " new version: " + newRules.getVersion());
        }
    }
}
