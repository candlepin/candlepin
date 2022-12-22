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
package org.candlepin.resource.util;

import org.candlepin.exceptions.BadRequestException;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Date;

import javax.xml.bind.DatatypeConverter;

/**
 * ResourceDate
 */
public class ResourceDateParser {

    private ResourceDateParser() {
    }

    public static Date parseDateString(I18n i18n, String activeOn) {
        Date d;
        if (StringUtils.isBlank(activeOn)) {
            return null;
        }

        try {
            d = DatatypeConverter.parseDateTime(activeOn).getTime();
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(i18n.tr("Invalid date, {0} must use ISO 8601 format", activeOn), e);
        }

        return d;
    }
}
