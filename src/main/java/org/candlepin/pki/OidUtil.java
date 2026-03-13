/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki;

import java.util.Collection;
import java.util.Optional;



/**
 * The OidUtil interface provides a standardized API for translating between Java security provider
 * algorithm names and their object identifiers (OIDs)
 */
public interface OidUtil {

    /**
     * Fetches the algorithm OID for the given key algorithm name. If the algorithm name does not map to a
     * known OID, or the backing security provider does not support the algorithm by name, this method returns
     * an empty optional. If the specified algorithm name is null or empty, this method throws an exception.
     *
     * @param algorithmName
     *  the key algorithm name to translate
     *
     * @throws IllegalArgumentException
     *  if the given algorithm name is null or empty
     *
     * @return
     *  an optional containing the OID of the specified key algorithm, or an empty optional if the algorithm
     *  name could not be converted to an OID.
     */
    Optional<String> getKeyAlgorithmOid(String algorithmName);

    /**
     * Fetches an algorithm name for the given key algorithm OID. If the algorithm OID is not known by the
     * underlying implementation, or does not map to a standard Java algorithm name, this method returns an
     * empty optional. If the specified algorithm OID is null or empty, this method throws an exception.
     * <p></p>
     * <strong>Note:</strong> While algorithm OIDs are generally univerally unique, a given algorithm OID may
     * have multiple names or aliases. If a mapping exists for a given OID, this method will return the same
     * value on repeated calls in a given environment; but this output is not guaranteed to be consistent
     * across environments or versions.
     *
     * @param algorithmOid
     *  the key algorithm OID to translate
     *
     * @throws IllegalArgumentException
     *  if the given algorithm OID is null or empty
     *
     * @return
     *  an optional containing the name of the specified key algorithm, or an empty optional if the algorithm
     *  OID could not be converted to a name.
     */
    Optional<String> getKeyAlgorithmName(String algorithmOid);

    /**
     * Fetches the algorithm OID for the given signature algorithm name. If the algorithm name does not map to
     * a known OID, or the backing security provider does not support the algorithm by name, this method
     * returns an empty optional. If the specified algorithm name is null or empty, this method throws an
     * exception.
     *
     * @throws IllegalArgumentException
     *  if the given algorithm name is null or empty
     *
     * @return
     *  an optional containing the OID of the specified key algorithm, or an empty optional if the algorithm
     *  name could not be converted to an OID.
     */
    Optional<String> getSignatureAlgorithmOid(String algorithmName);

    /**
     * Fetches an algorithm name for the given signature algorithm OID. If the algorithm OID is not known by
     * the underlying implementation, or does not map to a standard Java algorithm name, this method returns
     * an empty optional. If the specified algorithm OID is null or empty, this method throws an exception.
     * <p></p>
     * <strong>Note:</strong> While algorithm OIDs are generally univerally unique, a given algorithm OID may
     * have multiple names or aliases. If a mapping exists for a given OID, this method will return the same
     * value on repeated calls in a given environment; but this output is not guaranteed to be consistent
     * across environments or versions.
     *
     * @param algorithmOid
     *  the signature algorithm OID to translate
     *
     * @throws IllegalArgumentException
     *  if the given algorithm OID is null or empty
     *
     * @return
     *  an optional containing the name of the specified signature algorithm, or an empty optional if the
     *  algorithm OID could not be converted to a name.
     */
    Optional<String> getSignatureAlgorithmName(String algorithmOid);

    /**
     * Checks if the given collection of algorithm OIDs indicates support for the specified algorithm OID. If
     * any input into this method is null, this method throws an exception.
     *
     * @param supportedAlgorithmOids
     *  a collection of algorithm OIDs indicating support for a given environment
     *
     * @param algorithmOid
     *  the algorithm OID to test for support
     *
     * @throws IllegalArgumentException
     *  if supportedAlgorithmOids is null, or algorithmOid is null or empty
     *
     * @return
     *  true if the collection of algorithm OIDs indicates support for the given algorithm OID; false
     *  otherwise
     */
    boolean isAlgorithmSupported(Collection<String> supportedAlgorithmOids, String algorithmOid);

}
