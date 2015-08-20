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
package org.candlepin.common.test;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Hamcrest matcher that matches input against a given regex.  Note that the
 * {@link java.util.regex.Matcher#lookingAt()} method is used so that true will
 * be returned if the input begins with a sequence matching the regex.
 */
public class RegexMatcher extends TypeSafeMatcher<String> {
    private final String regex;

    public RegexMatcher(final String regex) {
        this.regex = regex;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("matches regex='" + regex + "'");
    }

    @Override
    protected boolean matchesSafely(String item) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(item);
        return m.lookingAt();
    }

    public static RegexMatcher matchesRegex(final String regex) {
        return new RegexMatcher(regex);
    }
}
