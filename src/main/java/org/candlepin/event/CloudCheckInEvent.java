package org.candlepin.event;

import org.candlepin.service.model.Event;
import org.candlepin.service.model.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class CloudCheckInEvent implements Event {
    private static final Logger log = LoggerFactory.getLogger(CloudCheckInEvent.class);

    private String systemUuid;
    private Date checkIn;
    // Hard coding for this PoC
    private String cloudProviderId = "AWS";
    private String cloudAccountId;
    private Collection<String> cloudOfferingIds = new ArrayList<>();

    @Override
    public String getBody() {
        return "{" +
            "\"system-uuid\":\"" + systemUuid + "\"," +
            "\"check-in\":\"" + checkIn.toString() + "\"," +
            "\"cloudProviderId\":\"" + cloudProviderId +"\"," +
            "\"cloudAccountId\":\"" + cloudAccountId + "\"," +
            "\"cloudOfferingIds\":[" + getOfferingString() + "]" +
            "}";
    }

    @Override
    public EventType getEventType() {
        return EventType.CLOUD_SYSTEM_CHECK_IN;
    }

    public CloudCheckInEvent setSystemUuid(String uuid) {
        this.systemUuid = uuid;
        return this;
    }

    public CloudCheckInEvent setCheckIn(Date checkIn) {
        this.checkIn = checkIn;
        return this;
    }

    public CloudCheckInEvent setCloudAccount(String cloudAccountId) {
        this.cloudAccountId = cloudAccountId;
        return this;
    }

    public CloudCheckInEvent setCloudOfferingIds(Collection<String> ids) {
        this.cloudOfferingIds = ids;
        return this;
    }

    private String getOfferingString() {
        if (cloudOfferingIds.isEmpty()) {
            return "";
        }

        String combined = "";
        for (String offerId : cloudOfferingIds) {
            combined = combined + "," + "\"" + offerId + "\"";
        }

        combined = combined.replaceFirst(",", "");

        return combined;
    }

}
