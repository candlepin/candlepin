package org.candlepin.service.model;

import java.util.List;

public interface CloudConsumerInfo {

    String getCloudAccountId();

    String getCloudInstanceId();

    List<String> getCloudOfferingIds();

}
