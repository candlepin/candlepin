package org.candlepin.messaging.impl.noop;

import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionManager;

public class NoopSessionManager implements CPMSessionManager {

    @Override
    public void initialize() throws CPMException {
        // Intentionally left blank
    }

    @Override
    public CPMSession createSession(boolean transactional) throws CPMException {
        return null;
    }

    @Override
    public boolean closeAllSessions() {
        return true;
    }

}
