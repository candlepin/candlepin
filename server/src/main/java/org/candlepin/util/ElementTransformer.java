/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.util;



/**
 * The ElementTransformer interface is used with the CandlepinQuery objects to provide
 * transformation services for each element.
 *
 * @param <I>
 *  The element type to be processed by this transformer
 *
 * @param <O>
 *  The element type to be output by this transformer
 */
public interface ElementTransformer<I, O> {

    /**
     * Transforms a given element.
     *
     * @param element
     *  The element to transform
     *
     * @return
     *  the transformed element
     */
    O transform(I element);

}
