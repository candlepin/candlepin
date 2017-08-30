/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto;

import org.candlepin.model.ModelEntity;



/**
 * The EntityTranslator interface defines a set of standard functionality to be provided by all
 * translator instances
 *
 * @param <I>
 *  The class of input model objects this translator supports
 *
 * @param <O>
 *  The class of DTO objects output by this translator
 */
public interface EntityTranslator<I extends ModelEntity, O extends CandlepinDTO> {

    /**
     * Translates the given source object to a DTO, omitting any nested model objects.
     *
     * @param source
     *  The source object to translate
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a new DTO representing the translated object, or null if the source object is null
     */
    O translate(I source);

    /**
     * Translates the given source object to a DTO, using the provided DTO factory to translate any
     * nested model objects. If a factory is not provided, then any nested model objects will be
     * omitted from the resultant DTO.
     *
     * @param factory
     *  The DTO factory to use for translating nested objects in the source object
     *
     * @param source
     *  The source object to translate
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a new DTO representing the translated object, or null if the source object is null
     */
    O translate(DTOFactory factory, I source);

    /**
     * Populates the given DTO object with data from the source object, omitting any nested model
     * objects, setting their respective fields to null.
     *
     * @param source
     *  The model object to translate
     *
     * @param destination
     *  The DTO to populate with data from the source object
     *
     * @throws IllegalArgumentException
     *  if either source or destination are null
     *
     * @return
     *  The populated DTO
     */
    O populate(I source, O destination);

    /**
     * Translates the given source object to a DTO, using the provided factory to translate any
     * nested model objects. If a factory is not provided, then any nested model objects will be
     * omitted from the resultant DTO and their respective fields will be set to null.
     *
     * @param factory
     *  The DTOFactory to use for translating nested model objects
     *
     * @param source
     *  The source object to use to populate the DTO
     *
     * @param destination
     *  The DTO to populate with data from the source object
     *
     * @throws IllegalArgumentException
     *  if either source or destination are null
     *
     * @return
     *  The populated DTO
     */
    O populate(DTOFactory factory, I source, O destination);

}
