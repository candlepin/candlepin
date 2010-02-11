package org.fedoraproject.candlepin.util;

import org.fedoraproject.candlepin.DateSource;

import java.util.Date;

public class DateSourceImpl implements DateSource {
    @Override
    public Date currentDate() {
        return new Date();
    }
}
