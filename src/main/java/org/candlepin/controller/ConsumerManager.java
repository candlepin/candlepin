package org.candlepin.controller;


import java.util.Date;

import javax.inject.Inject;

import org.candlepin.event.CloudCheckInEvent;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCloudDataCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.exception.EventPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerManager {
    private static final Logger log = LoggerFactory.getLogger(ConsumerManager.class);

    private ConsumerCurator consumerCurator;
    private ConsumerCloudDataCurator cloudDataCurator;
    private EventAdapter eventAdapter;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator, ConsumerCloudDataCurator cloudDataCurator, EventAdapter eventAdapter) {
        this.consumerCurator = consumerCurator;
        this.cloudDataCurator = cloudDataCurator;
        this.eventAdapter = eventAdapter;
    }

    public void updateLastCheckIn(Consumer consumer) throws EventPublishException {
        if (consumer == null) {
            return;
        }

        Date checkIn = new Date();
        consumerCurator.updateLastCheckin(consumer, checkIn);

        ConsumerCloudData cloudData = cloudDataCurator.getByConsumerId(consumer.getId());
        if (cloudData != null) {
            log.info("Cloud system check-in event. Attempting to publish event.");

            CloudCheckInEvent event = new CloudCheckInEvent()
                .setCheckIn(checkIn)
                .setCloudAccount(cloudData.getCloudAccountId())
                .setSystemUuid(consumer.getUuid())
                .setCloudOfferingIds(cloudData.getCloudOfferingIds());

            eventAdapter.publish(event);
        }
    }

}
