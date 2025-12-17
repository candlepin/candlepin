package org.candlepin.messaging;

public interface CPMSessionManager {

    // A connection can create many Sessions
    // A session can create many Producers
    // A session can create many Consumers

    CPMSession createSession(boolean transactional) throws CPMException;

}
