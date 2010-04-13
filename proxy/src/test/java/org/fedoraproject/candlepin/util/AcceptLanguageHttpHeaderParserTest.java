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

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * AcceptLanguageHttpHeaderParserTest
 */
public class AcceptLanguageHttpHeaderParserTest {

    @Test
    public void shouldParseASingleLanguage() {
        List<Language> parsed = AcceptLanguageHttpHeaderParser.parseHeader("en-gb");
        
        assertEquals(1, parsed.size());
        assertEquals(new Language("en-gb", new Float(0)), parsed.get(0));
    }
    
    @Test
    public void shouldParseASingleLanguageWithQuality() {
        List<Language> parsed = 
            AcceptLanguageHttpHeaderParser.parseHeader("en-gb;q=0.7");
        
        assertEquals(1, parsed.size());
        assertEquals(new Language("en-gb", new Float(0.7)), parsed.get(0));
    }
    
    @Test
    public void shouldParseASingleLanguageWithSpacesAroundQuality() {
        List<Language> parsed = 
            AcceptLanguageHttpHeaderParser.parseHeader("en-gb; q = 0.7");
        
        assertEquals(1, parsed.size());
        assertEquals(new Language("en-gb", new Float(0.7)), parsed.get(0));
    }
    
    @Test
    public void shouldParseMultipleLanguages() {
        List<Language> parsed = 
            AcceptLanguageHttpHeaderParser.parseHeader("en-gb;q=0.7, en, da;q=0.8");
        
        assertEquals(3, parsed.size());
        assertEquals(new Language("en-gb", new Float(0.7)), parsed.get(0));
        assertEquals(new Language("en", new Float(0)), parsed.get(1));
        assertEquals(new Language("da", new Float(0.8)), parsed.get(2));
    }
    
    @Test
    public void shouldSortByWeight() {
        List<Language> parsed = 
            AcceptLanguageHttpHeaderParser.parseHeader("en-gb;q=0.7, en;q=0.9, da;q=0.8");
        AcceptLanguageHttpHeaderParser.sortByWeight(parsed);
        
        assertEquals(3, parsed.size());
        assertEquals(new Language("en", new Float(0.9)), parsed.get(0));
        assertEquals(new Language("da", new Float(0.8)), parsed.get(1));
        assertEquals(new Language("en-gb", new Float(0.7)), parsed.get(2));
    }
}
