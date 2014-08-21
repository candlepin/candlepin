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

package org.candlepin.gutterball;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.report.Report;

import com.mongodb.BasicDBObject;

import org.apache.commons.lang.RandomStringUtils;

import java.util.Date;
import java.util.Random;

public class TestUtils {

    private TestUtils() {
    }

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static int randomInt() {
        return Math.abs(RANDOM.nextInt());
    }

    public static String randomString(String prefix) {
        return prefix + "-" + RandomStringUtils.randomAlphabetic(16);
    }

    public static Event createEvent(String type) {
        Event e = new Event();
        e.setId(randomString("ID"));
        e.setConsumerId(randomString("My Test Consumer"));
        e.setType("CREATE");
        e.setMessageText("This is a message");
        e.setTimestamp(new Date());
        return e;
    }

    public static Report mockReport(String key, String desc) {
        Report r = mock(Report.class);
        when(r.getKey()).thenReturn(key);
        when(r.getDescription()).thenReturn(desc);
        return r;
    }

    public static BasicDBObject createComplianceSnapshot(Date statusDate, String consumerUuid, String owner,
            String statusString) {
        // NOTE: Does not return DBObject interface since the curator needs this BasicDBObject since it
        //       requires a concrete class when calling setObjectClass().
        // NOTE: Currently only contains enough data to satisfy the ConsumerStatusReport

        BasicDBObject consumer = new BasicDBObject();
        consumer.append("uuid", consumerUuid);
        consumer.append("owner", new BasicDBObject("key", owner));

        BasicDBObject status = new BasicDBObject();
        status.append("date", statusDate);
        status.append("status", statusString);

        BasicDBObject snap = new BasicDBObject();
        snap.append("consumer", consumer);
        snap.append("status", status);
        return snap;
    }
}
