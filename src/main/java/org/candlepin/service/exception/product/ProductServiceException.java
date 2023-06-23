/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.exception.product;

import org.candlepin.service.exception.ServiceException;



/**
 * The ProductServiceException is used to as a base for all exceptions returned from
 * the product service adapter.
 */
public class ProductServiceException extends ServiceException {
    String productId;
    /**
     * Constructs a new exception with null as its detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     */
    public ProductServiceException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param productId
     *  the id of the product requested from the adapter
     */
    public ProductServiceException(String productId) {
        super();
        this.productId = productId;
    }

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to initCause(java.lang.Throwable).
     *
     * @param productId
     *  the id of the product requested from the adapter
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     */
    public ProductServiceException(String productId, String message) {
        super(message);
        this.productId = productId;
    }

    /**
     * Constructs a new exception with the specified cause and a detail message of
     * <tt>(cause == null ? null : cause.toString())</tt> (which typically contains the and
     * detail message of cause). This constructor is useful for exceptions that are little more
     * than wrappers for other throwables (for example, PrivilegedActionException).
     *
     * @param productId
     *  the id of the product requested from the adapter
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public ProductServiceException(String productId, Throwable cause) {
        super(cause);
        this.productId = productId;
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * <p></p>
     * Note that the detail message associated with cause is not automatically incorporated in this
     * exception's detail message.
     *
     * @param productId
     *  the id of the product requested from the adapter
     *
     * @param message
     *  the detail message. The detail message is saved for later retrieval by the getMessage()
     *  method.
     *
     * @param cause
     *  the cause (which is saved for later retrieval by the Throwable.getCause() method). A null
     *  value is permitted, and indicates that the cause is nonexistent or unknown.
     */
    public ProductServiceException(String productId, String message, Throwable cause) {
        super(message, cause);
        this.productId = productId;
    }

    public String getProductId() {
        return this.productId;
    }
}
