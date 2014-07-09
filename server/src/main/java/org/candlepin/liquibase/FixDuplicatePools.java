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
package org.candlepin.liquibase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import liquibase.database.jvm.JdbcConnection;

/**
 * FixDuplicatePools class to fix duplicate pool data.
 *
 * This class is disconnected from its liquibase wrapper
 * so that it may be used more easily as a one-off script
 * in scenarios that do not use liquibase.
 */
public class FixDuplicatePools {

    private JdbcConnection conn;
    private final CustomTaskLogger log;

    public FixDuplicatePools(JdbcConnection conn) {
        this.conn = conn;
        this.log = new SystemOutLogger();
    }

    public FixDuplicatePools(JdbcConnection conn, CustomTaskLogger log) {
        this.conn = conn;
        this.log = log;
    }

    /*
     * When we delete pools, we need to reassign entitlements and mark them dirty.
     * Otherwise they will contain a bad pool id.
     *
     * ActivationKeyPool will be deleted by the cascade
     * source entitlement shouldn't exist for anything with nonnull subid and subkey
     *
     * Make sure that Branding is also deleted on cascade on the database level,
     * hibernate annotations don't apply
     *
     */
    public void execute() throws Exception {
        log.info("--- Running update script ---");
        // Get a map of source subscription to pool ids
        Map<SubPair, List<String>> subPoolsMap = getSubPoolMap();
        log.info("Found " + subPoolsMap.keySet().size() + " subscriptions " +
            "with duplicate pools.");
        // Nothing to do if there aren't any duplicates
        if (!subPoolsMap.isEmpty()) {
            for (Entry<SubPair, List<String>> entry : subPoolsMap.entrySet()) {
                SubPair sub = entry.getKey();
                List<String> ids = entry.getValue();
                List<String> poolsToRemove = new LinkedList<String>();

                // Keep one pool
                String poolToKeep = ids.get(0);
                // Remove the rest
                for (int i = 1, len = ids.size(); i < len; i++) {
                    poolsToRemove.add(ids.get(i));
                }
                log.info("Removing " + poolsToRemove.size() +
                    " pools for subscription " + sub);
                updateEntsForIds(poolsToRemove, poolToKeep);
                removePoolsWithIds(poolsToRemove);
            }
        }
        log.info("--- Finished update script ---");
    }

    private int removePoolsWithIds(List<String> dupeIds) throws Exception {
        String sql = "DELETE FROM cp_pool " +
            "WHERE id IN (";
        for (int i = 0, len = dupeIds.size(); i < len; i++) {
            sql += "?";
            if (i < len - 1) {
                sql += ", ";
            }
        }
        sql += ")";
        PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0, max = dupeIds.size(); i < max; i++) {
            stmt.setString(i + 1, dupeIds.get(i));
        }
        return stmt.executeUpdate();
    }

    private int updateEntsForIds(List<String> dupeIds, String goodId) throws Exception {
        String sql = "UPDATE cp_entitlement " +
            "SET pool_id=?, dirty=? " +
            "WHERE pool_id IN (";
        for (int i = 0, len = dupeIds.size(); i < len; i++) {
            sql += "?";
            if (i != len - 1) {
                sql += ", ";
            }
        }
        sql += ")";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, goodId);
        // Different databases represent booleans differently.  Even though this
        // is a static value, we need to let the jdbc driver handle it
        stmt.setBoolean(2, true);
        for (int i = 0, max = dupeIds.size(); i < max; i++) {
            stmt.setString(i + 3, dupeIds.get(i));
        }
        return stmt.executeUpdate();
    }

    /*
     * Builds a map of subid-subkey -> [ids, with, duplicates]
     */
    private Map<SubPair, List<String>> getSubPoolMap() throws Exception {
        Statement stmt = conn.createStatement();
        Map<SubPair, List<String>> subPoolsMap = new HashMap<SubPair, List<String>>();
        ResultSet rs = stmt.executeQuery(
            "SELECT cp_pool.id, cp_pool.subscriptionid, " +
            "cp_pool.subscriptionsubkey, cp_pool.owner_id " +
            "FROM cp_pool, " +
            "(SELECT subscriptionid, subscriptionsubkey " +
            "FROM cp_pool " +
            "WHERE subscriptionid IS NOT NULL " +
            "AND subscriptionsubkey IS NOT NULL " +
            "GROUP BY subscriptionid, subscriptionsubkey " +
            "HAVING count(id) > 1) subs " +
            "WHERE subs.subscriptionid = cp_pool.subscriptionid " +
            "AND subs.subscriptionsubkey = cp_pool.subscriptionsubkey");

        // We need to be completely sure that we aren't moving subscriptions
        // from one owner to another.
        Map<SubPair, String> subOwners = new HashMap<SubPair, String>();

        while (rs.next()) {
            SubPair current = new SubPair(rs.getString(2), rs.getString(3));
            if (!subPoolsMap.containsKey(current)) {
                subPoolsMap.put(current, new LinkedList<String>());
                subOwners.put(current, rs.getString(4));
            }
            else if (!subOwners.get(current).equals(rs.getString(4))) {
                // Make sure owners are the same
                log.error("Owners '" + rs.getString(4) + "' and '" +
                    subOwners.get(current) + "' both have pools from subscription " +
                    current);
                throw new Exception("Pools exist for subscription " + current + " within " +
                    "multiple owners.");
            }
            subPoolsMap.get(current).add(rs.getString(1));
        }
        return subPoolsMap;
    }

    /**
     * Conainer for subscriptionid/subscriptionsubkey values
     *
     * I suppose concatenating the strings in our sql query would do
     * this just as well...
     */
    public class SubPair {
        private String subscriptionId;
        private String subscriptionSubKey;

        public SubPair(String id, String key) {
            this.subscriptionId = id;
            this.subscriptionSubKey = key;
        }

        @Override
        public boolean equals(Object o) {
            if (o != null && o instanceof SubPair) {
                SubPair other = (SubPair) o;
                return this.subscriptionId.equals(other.subscriptionId) &&
                    this.subscriptionSubKey.equals(other.subscriptionSubKey);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (subscriptionId + subscriptionSubKey).hashCode();
        }

        @Override
        public String toString() {
            return this.subscriptionId + "-" + this.subscriptionSubKey;
        }
    }
}
