/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.util.StdConverter;


/**
 * This class trims strings that are being deserialized by Jackson.  It is primarily
 * necessitated by BZ 1405125 (ppc64 clients are reporting a virt uuid in the facts that
 * ends in a null byte).
 */
public class StringTrimmingConverter extends StdConverter<String, String> {
    private static final Logger log = LoggerFactory.getLogger(StringTrimmingConverter.class);

    @Override
    public String convert(String value) {
        return (value == null) ? value : value.trim();
    }
}
