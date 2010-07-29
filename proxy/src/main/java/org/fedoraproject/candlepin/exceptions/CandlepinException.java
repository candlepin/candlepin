/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.exceptions;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;


/**
 * Base class for runtime exceptions thrown by Resources.
 */
public abstract class CandlepinException extends RuntimeException {
    private static final long serialVersionUID = -3430329252623764984L;
    private final Status returnCode;
    
    private final ExceptionMessage message;
    
    public CandlepinException(Status returnCode, String message) {
       this(returnCode, message, null);
    }
    
    public CandlepinException(Status returnCode, String message, Throwable e) {
        super(e);
        this.returnCode = returnCode;
        this.message = new ExceptionMessage(message);
    }

    public ExceptionMessage message() {
        return message;
    }
    
    public Status httpReturnCode() {
        return returnCode;
    }
    
    public Map<String, String> headers() {
        return new HashMap<String, String>();
    }
}
