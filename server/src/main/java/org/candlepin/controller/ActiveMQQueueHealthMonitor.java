

package org.candlepin.controller;

import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.candlepin.audit.ActiveMQStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ActiveMQQueueHealthMonitor implements Runnable, CloseListener {
    private static Logger log = LoggerFactory.getLogger(ActiveMQStatusMonitor.class);
    private List<ActiveMQQueueHealthListener> registeredListeners;
    private ActiveMQStatus lastReported;

    public void registerListener(ActiveMQQueueHealthListener listener) {
        this.registeredListeners.add(listener);
    }
    @Override
    public void run() {

    }

    @Override
    public void connectionClosed() {
        notifyListeners(ActiveMQStatus.UNHEALTHY);
        monitorConnection();
    }

    private void notifyListeners(ActiveMQStatus newStatus) {
        log.debug("Notifying listeners of new status: {}", newStatus);
        this.registeredListeners.forEach(listener -> {
            try {
                listener.onStatusUpdate(lastReported, newStatus);
            }
            catch (Exception e) {
                // If the listener throws an exception, log it and move on to the next.
                log.error("Unable to notify listener about new status: {}", listener.getClass(), e);
            }
        });
        lastReported = newStatus;
    }
}
