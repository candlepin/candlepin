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

package org.candlepin.gutterball;

import com.mongodb.DB;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Junit rule that will clean up all specified collections after each
 * test run. It relies on an {@link EmbeddedMongoRule} to provide a
 * connection to the embedded mongo instance.
 */
public class MongoCollectionCleanupRule extends ExternalResource {
    private static Logger log = LoggerFactory.getLogger(MongoCollectionCleanupRule.class);

    private EmbeddedMongoRule embeddedRule;
    private String[] collectionsToDrop;

    public MongoCollectionCleanupRule(EmbeddedMongoRule embeddedRule,
            String ... collectionsToDrop) {
        this.embeddedRule = embeddedRule;
        this.collectionsToDrop = collectionsToDrop;
    }

    @Override
    protected void after() {
        DB db = embeddedRule.getMongoConnection().getDB();
        for (String toDrop : this.collectionsToDrop) {
            if (db.collectionExists(toDrop)) {
                log.info("Dropping collection: " + toDrop);
                db.getCollection(toDrop).drop();
            }
        }
    }

}
