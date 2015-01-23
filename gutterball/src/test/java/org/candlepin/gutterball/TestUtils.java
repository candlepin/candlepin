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

import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceReason;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Owner;
import org.candlepin.gutterball.report.Report;

import org.apache.commons.lang.RandomStringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    public static Report mockReport(String key, String desc) {
        Report r = mock(Report.class);
        when(r.getKey()).thenReturn(key);
        when(r.getDescription()).thenReturn(desc);
        return r;
    }

    public static Compliance createComplianceSnapshot(Date statusDate, String consumerUuid,
            String owner, String statusString) {
        return createComplianceSnapshot(statusDate, consumerUuid, owner, statusString, null);
    }

    public static Compliance createComplianceSnapshot(Date statusDate, String consumerUuid,
            String owner, String statusString, ConsumerState state) {
        Consumer consumerSnap = new Consumer(consumerUuid, null, createOwnerSnapshot(owner, owner));
        consumerSnap.setConsumerState(state);
        ComplianceStatus statusSnap = new ComplianceStatus(statusDate, statusString);

        if (statusString.toLowerCase().equals("invalid")) {
            ComplianceReason reason = new ComplianceReason("reason-key", "Test message");
            reason.setComplianceStatus(statusSnap);
            statusSnap.getReasons().add(reason);
        }
        return new Compliance(statusDate, consumerSnap, statusSnap);
    }

    public static Owner createOwnerSnapshot(String key, String displayName) {
        return new Owner(key, displayName);
    }

    public static List<String> getUuidsFromSnapshots(List<Compliance> snaps) {
        List<String> uuids = new ArrayList<String>();
        for (Compliance cs : snaps) {
            uuids.add(cs.getConsumer().getUuid());
        }
        return uuids;
    }

}
