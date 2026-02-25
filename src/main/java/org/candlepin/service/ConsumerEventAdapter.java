package org.candlepin.service;

import org.candlepin.service.model.ConsumerInfo;

public interface ConsumerEventAdapter {

    void createCheckInEvent(ConsumerInfo consumer);

}
