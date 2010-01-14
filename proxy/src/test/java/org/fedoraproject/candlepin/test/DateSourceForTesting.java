package org.fedoraproject.candlepin.test;

import java.util.Calendar;
import java.util.Date;

import org.fedoraproject.candlepin.DateSource;

public class DateSourceForTesting implements DateSource {
    private Date currentDate;

    public DateSourceForTesting() {
        currentDate = null;
    }
    
    public DateSourceForTesting(Date dateToReturn) {
        this.currentDate = dateToReturn;
    }
    
    public DateSourceForTesting(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, 0, 0, 0);
        currentDate = calendar.getTime();
    }
    
    public void currentDate(Date date) {
        this.currentDate = date;
    }
    
    public Date currentDate() {
        return currentDate;
    }
}
