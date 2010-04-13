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
package org.fedoraproject.candlepin.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * HttpHeaderParser
 */
public class AcceptLanguageHttpHeaderParser {

    private AcceptLanguageHttpHeaderParser() {
    }
    
    public static List<Language> parseHeader(String header) {
        String[] languages = header.split(",");
        List<Language> parsed = new ArrayList<Language>(languages.length);
        for (String language : languages) {
            parsed.add(parseLanguage(language));
        }
        
        return parsed;
    }
    
    public static void sortByWeight(List<Language> toSort) {
        if (toSort == null || toSort.size() < 1) { 
            return;
        }
        Collections.sort(toSort, new LanguageComparator());
    }
    
    private static Language parseLanguage(String language) {
        String[] codeAndQ = language.split(";\\s*q\\s*=\\s*");
        return codeAndQ.length == 1 ?
            new Language(codeAndQ[0].trim(), new Float(0)) :
            new Language(codeAndQ[0].trim(), Float.parseFloat(codeAndQ[1]));    
    }
    
    private static class LanguageComparator implements Comparator<Language> {
        @Override
        public int compare(Language language2, Language language) {
            return language.q().compareTo(language2.q());
        }
    }
}
