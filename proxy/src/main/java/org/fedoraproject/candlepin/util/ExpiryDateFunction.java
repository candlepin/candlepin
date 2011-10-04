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

import java.util.Calendar;
import java.util.Date;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;

import com.google.common.base.Function;
import com.google.inject.Inject;

/**
 * ExpiryDateFunction
 */
public class ExpiryDateFunction implements Function<Date, Date> {

    private int yrAddendum;

    //for now, its just years
    public ExpiryDateFunction(int years) {
        this.yrAddendum = years;
    }

    @Inject
    public ExpiryDateFunction(Config config) {
        this(config.getInt(ConfigProperties.IDENTITY_CERT_YEAR_ADDENDUM));
    }
    /* (non-Javadoc)
     * @see com.google.common.base.Function#apply(java.lang.Object)
     */
    @Override
    public Date apply(Date arg0) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime((Date) arg0.clone());
        calendar.add(Calendar.YEAR, yrAddendum);
        return calendar.getTime();
    }

}
