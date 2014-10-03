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
package org.candlepin.common.exceptions;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;


/**
 * Base class for runtime exceptions thrown by Resources.
 */
public class CandlepinException extends RuntimeException {
    private static final long serialVersionUID = -3430329252623764984L;
    private final Status returnCode;
    private final boolean logException;

    protected final ExceptionMessage message;

    public CandlepinException(Status returnCode, String message) {
        this(returnCode, message, null);
    }

    public CandlepinException(Status returnCode, String message, boolean logException) {
        this(returnCode, message, logException, null);
    }

    // ctor for sending in a subclassed ExceptionMessage
    public CandlepinException(Status returnCode, ExceptionMessage em) {
        this(returnCode, em, true);
    }

    public CandlepinException(Status returnCode, ExceptionMessage em, boolean logException) {
        super(em.getDisplayMessage(), null);
        this.returnCode = returnCode;
        this.message = em;
        this.logException = logException;
    }


    public CandlepinException(Status returnCode, String message, Throwable e) {
        this(returnCode, message, true, e);
    }

    public CandlepinException(Status returnCode, String message, boolean logException, Throwable e) {
        super(message, e);
        this.returnCode = returnCode;
        this.message = new ExceptionMessage(message);
        this.logException = logException;
    }

    public ExceptionMessage message() {
        return message;
    }

    public Status httpReturnCode() {
        return returnCode;
    }
    /**
     * Add the ability for exceptions to set headers in the response. This
     * allows me to use basic auth from the browser. Should be overridden
     * by child exceptions if they want to include headers.
     * @return headers
     */
    public Map<String, String> headers() {
        return new HashMap<String, String>();
    }

    public boolean isLogException() {
        return logException;
    }

}
