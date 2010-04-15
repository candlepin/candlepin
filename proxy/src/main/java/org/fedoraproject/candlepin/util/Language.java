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

/**
 * used to hold the results of parsing of Accept-Language http header
 */
public class Language {
    private String language;
    private Float q;

    public Language(String language, Float q) {
        this.language = language;
        this.q = q;
    }
    
    public String language() {
        return language;
    }
    
    public Float q() {
        return q;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Language)) {
            return false;
        }
        
        Language another = (Language) o;
        
        return language.equals(another.language) && q.equals(another.q);
    }
    
    @Override
    public int hashCode() {
        return language.hashCode() * 31 + q.hashCode();
    }
    
    @Override
    public String toString() {
        return language + ";q=" + q.toString();
    }
}
