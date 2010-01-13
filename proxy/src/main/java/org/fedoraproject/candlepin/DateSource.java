package org.fedoraproject.candlepin;

import java.util.Date;

public interface DateSource {
    public Date currentDate();
}
