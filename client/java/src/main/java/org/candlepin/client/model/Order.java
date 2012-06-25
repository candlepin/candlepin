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
package org.candlepin.client.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.math.NumberUtils;

/**
 * Order
 */
public class Order {
    private Extensions ex;
    private SimpleDateFormat iso8601DateFormat;

    /**
     * @param extensions
     */
    public Order(Extensions extensions) {
        this.ex = extensions;
        iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String getName() {
        return ex.getValue("1");
    }

    public int getOrderNumber() {
        return NumberUtils.toInt(ex.getValue("2"), -1);
    }

    public String getSku() {
        return ex.getValue("3");
    }

    public String getRegnum() {
        return ex.getValue("4");
    }

    public int getQuantity() {
        return NumberUtils.toInt(ex.getValue("5"), -1);
    }

    public int getUsedQuantity() {
        return NumberUtils.toInt(ex.getValue("13").trim(), -1);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public Date getStartDate() {
        return parseDate(ex.getValue("6"));
    }

    public Date getEndDate() {
        return parseDate(ex.getValue("7"));
    }

    private Date parseDate(String date) {
        try {
            return iso8601DateFormat.parse(date.trim());
        }
        catch (ParseException e) {
            // returning the current time assuming both fields are bad will at least make
            // the cert invalid
            return new Date();
        }
    }
}
