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
package org.fedoraproject.candlepin.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.model.RulesCurator;

import com.google.inject.Inject;

/**
 * RulesImporter
 */
public class RulesImporter {
    private static Logger log = Logger.getLogger(RulesImporter.class);
    
    private RulesCurator curator;

    @Inject
    RulesImporter(RulesCurator curator) {
        this.curator = curator;
    }

    public Rules importObject(Reader reader) throws IOException {
        log.debug("Importing rules file");
        new BufferedReader(reader);
        
        return curator.update(new Rules(StringFromReader.asString(reader)));
    }
}
