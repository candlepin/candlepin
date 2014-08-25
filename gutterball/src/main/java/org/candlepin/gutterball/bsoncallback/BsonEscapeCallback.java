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
package org.candlepin.gutterball.bsoncallback;

import com.mongodb.DBCallback;
import com.mongodb.util.JSONCallback;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BsonEscapeCallback used to replace dots with a character
 * sequence that is acceptable to mongodb
 */
public class BsonEscapeCallback extends JSONCallback implements DBCallback {

    private static final String[][] REPLACEMENTS = new String[][] {
        {".", "_dot_"},
        {"$", "_dolla_"}
    };

    private final int toReplaceIdx;
    private final int replacementIdx;
    private final Matcher matchesAny;

    protected final boolean write;

    public BsonEscapeCallback() {
        this(true);
    }

    public BsonEscapeCallback(boolean write) {
        // Save write for other callbacks to check
        this.write = write;
        toReplaceIdx = write ? 0 : 1;
        replacementIdx = write ? 1 : 0;
        // Build our regex once (expensive) so we can check every key more quickly/efficiently
        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append(".*(?:");
        for (int i = 0; i < REPLACEMENTS.length; i++) {
            regexBuilder.append(Pattern.quote(REPLACEMENTS[i][toReplaceIdx]));
            if (i < REPLACEMENTS.length - 1) {
                regexBuilder.append('|');
            }
        }
        regexBuilder.append(").*");
        matchesAny = Pattern.compile(regexBuilder.toString()).matcher("");
    }

    protected String sanitizeName(String name) {
        // The idea here is that we have to check a LOT of names, and the vast majority
        // will not match, so we're only really concerned with checking quickly.
        if (matchesAny.reset(name).matches()) {
            for (String[] replPair : REPLACEMENTS) {
                if (name.contains(replPair[toReplaceIdx])) {
                    name = name.replace(replPair[toReplaceIdx], replPair[replacementIdx]);
                }
            }
        }
        // We want to return the original String if it doesn't match so we're
        // not re-allocating memory
        return name;
    }

    @Override
    @SuppressWarnings("checkstyle:methodname")
    protected void _put(String name, Object o) {
        super._put(sanitizeName(name), o);
    }

    @Override
    public void objectStart(boolean array, String name) {
        super.objectStart(array, sanitizeName(name));
    }
}
