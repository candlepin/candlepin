/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin.async;

/**
     * Enum representing known manager states, and valid state transitions
     */
    public enum ManagerState {
        // Impl note: We have to use strings here since we can't reference enums that haven't yet
        // been defined. This is slightly less efficient than I'd like, but whatever.
        CREATED("INITIALIZED", "SHUTDOWN"),
        INITIALIZED("RUNNING", "SUSPENDED", "SHUTDOWN"),
        RUNNING("RUNNING", "SUSPENDED", "SHUTDOWN"),
        SUSPENDED("RUNNING", "SUSPENDED", "SHUTDOWN"),
        SHUTDOWN();

        private final String[] transitions;

        ManagerState(String... transitions) {
            this.transitions = transitions != null && transitions.length > 0 ? transitions : null;
        }

        public boolean isValidTransition(ManagerState state) {
            if (state != null && this.transitions != null) {
                for (String transition : this.transitions) {
                    if (transition.equals(state.name())) {
                        return true;
                    }
                }
            }

            return false;
        }

        public boolean isTerminal() {
            return this.transitions == null;
        }
    }