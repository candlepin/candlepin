package org.fedoraproject.candlepin.util;

import java.util.Date;

import org.fedoraproject.candlepin.DateSource;

import com.google.inject.Singleton;

public class DateSourceImpl implements DateSource {
    @Override
    public Date currentDate() {
        return new Date();
    }
}
