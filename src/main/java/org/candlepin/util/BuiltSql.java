/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import java.util.Map;

/**
 * Immutable container for a dynamically constructed SQL (or JPQL/HQL) query string
 * and its associated named parameters.
 * <p>
 * This record is typically used to return both the query text and the parameters
 * from a query builder method, ensuring that parameter binding is always consistent
 * with the generated query.
 * <p>
 * By keeping the SQL string and its argument map together, you eliminate the risk of
 * "blind" parameter setting — parameters are only bound if they were actually added
 * during query construction.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Build query with conditional filters
 * Map<String, Object> args = new HashMap<>();
 * StringBuilder sql = new StringBuilder("SELECT * FROM cp_consumer WHERE owner_id = :owner_id");
 * args.put("owner_id", ownerId);
 *
 * if (afterId != null) {
 *     sql.append(" AND id > :after_id");
 *     args.put("after_id", afterId);
 * }
 *
 * BuiltSql built = new BuiltSql(sql.toString(), args);
 *
 * // Execute using JPA
 * Query q = entityManager.createNativeQuery(built.sql());
 * built.args().forEach(q::setParameter);
 *
 * List<Object[]> rows = q.getResultList();
 * }</pre>
 *
 * @param sql  the fully assembled SQL/JPQL/HQL query string, including any parameter placeholders
 * @param args a map of named parameter placeholders to their corresponding bound values
 */
public record BuiltSql(String sql, Map<String, Object> args) {}
