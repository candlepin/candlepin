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
package org.candlepin.gutterball.curator;

import org.candlepin.gutterball.bsoncallback.BsonEscapeCallback;

import com.mongodb.DBCallback;
import com.mongodb.DBCollection;
import com.mongodb.DBDecoder;
import com.mongodb.DBDecoderFactory;
import com.mongodb.DefaultDBDecoder;

/**
 * EscapingDBDecoder transforms data (mostly in facts) back into
 * the json we expect
 */
public class EscapingDBDecoder extends DefaultDBDecoder {

    /**
     * EscapingDBDecoderFactory to build EscapingDBDecoder objects
     */
    static class EscapingDBDecoderFactory implements DBDecoderFactory {
        @Override
        public DBDecoder create() {
            return new EscapingDBDecoder();
        }

        @Override
        public String toString() {
            return "EscapingDBDecoder.EscapeFactory";
        }
    }

    public static final DBDecoderFactory FACTORY = new EscapingDBDecoderFactory();

    public EscapingDBDecoder() {
        super();
    }

    @Override
    public DBCallback getDBCallback(DBCollection arg0) {
        return new BsonEscapeCallback(false);
    }

    @Override
    public String toString() {
        return "EscapingDBDecoder";
    }
}
