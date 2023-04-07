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
package org.candlepin.dto.api.v1;



/**
 * The LinkableDTO interface defines functionality for objects which are linkable via the REST
 * interface.
 */
public interface LinkableDTO {

    /**
     * Retrieves the link clients can use to link back to this object.
     *
     * @return
     *  The link clients can use to link back to this object, or null if no such link is available
     */
    String getHref();

}
