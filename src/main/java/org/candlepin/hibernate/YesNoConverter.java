/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.hibernate;

import org.hibernate.type.CharBooleanConverter;

import jakarta.persistence.Converter;

/**
 * Converts a yes/no character into a boolean in a case insensitive way. This class replaces
 * {@link org.hibernate.type.YesNoConverter} which is case sensitive.
 */
@Converter
public class YesNoConverter extends CharBooleanConverter {

    private static final String[] VALUES = {"n", "y"};

    @Override
    public Boolean toDomainValue(Character relationalForm) {
        if (relationalForm == null) {
            return null;
        }

        switch (relationalForm) {
            case 'Y':
            case 'y':
                return true;
            case 'N':
            case 'n':
                return false;
            default:
                throw new IllegalArgumentException("relational form is neither 'y' or 'n'");
        }
    }

    @Override
    public Character toRelationalValue(Boolean domainForm) {
        if (domainForm == null) {
            return null;
        }

        return domainForm ? 'y' : 'n';
    }

    @Override
    protected String[] getValues() {
        return VALUES;
    }

}

