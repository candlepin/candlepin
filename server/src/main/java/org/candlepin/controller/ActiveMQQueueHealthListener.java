

package org.candlepin.controller;

import org.candlepin.audit.ActiveMQStatus;

public interface ActiveMQQueueHealthListener {

    void onStatusUpdate(ActiveMQStatus oldStatus, ActiveMQStatus newStatus);
}
