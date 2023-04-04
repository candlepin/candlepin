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
package org.candlepin.controller.mode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;



/**
 * The CandlepinModeManager is responsible for transitioning Candlepin between normal and "suspend"
 * modes. While in suspend mode, only the status endpoint should be accessible, no async or cron
 * jobs should be triggered, and, generally speaking, all normal operations should be halted until
 * the reason for entering suspend mode was resolved.
 * <p></p>
 * Suspend mode should only be used for recoverable issues, such as a remote service temporarily
 * going offline. If a given issue is not reasonably recoverable, normal exception or error throwing
 * should be used instead.
 * <p></p>
 * When multiple services have requested Candlepin enter suspend mode, it will remain suspended
 * until all services have reported that they are free to resume operations. Additionally, a single
 * service can enter suspend mode for multiple reasons if it so desires, resuming only when all
 * detected issues are resolved.
 */
@Singleton
public class CandlepinModeManager {
    private static Logger log = LoggerFactory.getLogger(CandlepinModeManager.class);

    /**
     * The Mode enum represents Candlepin's possible operating modes.
     */
    public static enum Mode {
        /**
         * NORMAL mode is Candlepin's default operating mode. It has no special behaviors and
         * indicates everything is working as intended.
         */
        NORMAL,

        /**
         * SUSPEND mode is used to indicate a critical, but recoverable, problem with Candlepin
         * or one of its backing services. Client input should be blocked and background tasks
         * should be suspended until Candlepin returns to NORMAL mode.
         */
        SUSPEND
    }

    // Impl note:
    // If we ever add more modes in the future outside of NORMAL and SUSPEND, we'll probably want to
    // use a stacking mode change model, to prevent us from dropping out of various modes before
    // we're ready. This may also minimize the amount of API changes here, as getModeChangeReasons
    // would still make sense by just pulling the reasons for the mode on top of the stack.

    // With only two modes, we have no need to "store" the mode; If we have reasons, we're suspend,
    // otherwise, we're normal.
    private Map<String, Map<String, ModeChangeReason>> reasons;

    private Set<ModeChangeListener> listeners;

    /**
     * Creates a new CandlepinModeManager instance
     */
    public CandlepinModeManager() {
        this.reasons = new HashMap<>();
        this.listeners = new HashSet<>();
    }

    /**
     * Suspends operations using the given key and reason. The key must be provided when calling
     * resumeOperations to exit suspend mode later. If the key has already been used to suspend
     * Candlepin, this method updates the reason and time.
     *
     * @param reasonClass
     *  The class
     *
     * @param reason
     *  A short string identifying the specific reason for entering suspend mode
     *
     * @param message
     *  An optional, detailed message providing additional information for the reason Candlepin
     *  operations are suspended
     *
     * @return
     *  true if Candlepin enters suspend mode as a result of a call to this method; false if
     *  Candlepin is already in suspend mode at the time the method is called
     */
    public synchronized boolean suspendOperations(String reasonClass, String reason, String message) {
        if (reasonClass == null) {
            throw new IllegalArgumentException("reason class is null");
        }

        if (reason == null) {
            throw new IllegalArgumentException("reason string is null");
        }

        ModeChangeReason mcr = new ModeChangeReason(reasonClass, reason, new Date(), message);
        boolean suspended = this.reasons.isEmpty();

        Map<String, ModeChangeReason> classReasons = this.reasons.get(reasonClass);
        if (classReasons == null) {
            classReasons = new HashMap<>();
            this.reasons.put(reasonClass, classReasons);
        }

        boolean updated = classReasons.containsKey(reason);
        classReasons.put(reason, mcr);

        if (suspended) {
            log.warn("Candlepin is entering suspend mode. Reason: {}", mcr);
            this.notifyListeners(Mode.NORMAL, Mode.SUSPEND);
        }
        else {
            String type = updated ? "Updated" : "Additional";
            log.warn("{} reason received for maintaining suspend mode: {}", type, mcr);
        }

        return suspended;
    }

    /**
     * Suspends operations using the given key and reason. The key must be provided when calling
     * resumeOperations to exit suspend mode later. If the key has already been used to suspend
     * Candlepin, this method updates the reason and time.
     *
     * @param reasonClass
     *  The class of reason for entering suspend mode
     *
     * @param reason
     *  A short string identifying the reason for entering suspend mode
     *
     * @return
     *  true if Candlepin enters suspend mode as a result of a call to this method; false if
     *  Candlepin is already in suspend mode for another reason
     */
    public boolean suspendOperations(String reasonClass, String reason) {
        return this.suspendOperations(reasonClass, reason, null);
    }

    /**
     * Attempts to resume normal operations by marking all reasons of the specified class as
     * resolved. If all reasons have been resolved, operations will be resumed.
     *
     * @param reasonClass
     *  the class of reasons to resolve
     *
     * @throws IllegalArgumentException
     *  if reasonClass is null or empty
     *
     * @return
     *  true if Candlepin resumes normal operations as a result of a call to this method; false
     *  if Candlepin is still held in suspend mode for other reasons
     */
    public synchronized boolean resolveSuspendReasonClass(String reasonClass) {
        if (reasonClass == null || reasonClass.isEmpty()) {
            throw new IllegalArgumentException("reason class is null or empty");
        }

        Map<String, ModeChangeReason> classReasons = this.reasons.get(reasonClass);
        boolean resumed = false;

        if (classReasons != null) {
            log.info("Suspend reason class resolved: {} ({} reason(s))", reasonClass, classReasons.size());

            this.reasons.remove(reasonClass);

            if (this.reasons.isEmpty()) {
                resumed = true;

                log.info("Candlepin is resuming normal mode");
                this.notifyListeners(Mode.SUSPEND, Mode.NORMAL);
            }
        }

        return resumed;
    }

    /**
     * Attempts to resume normal operations by marking the specified reason of the given class as
     * resolved. If all reasons have been resolved, operations will be resumed. If the reason is not
     * one holding Candlepin in suspend mode, or Candlepin is not in suspend mode, this method will
     * return false.
     *
     * @param reasonClass
     *  the class of reason to which the specified reason belongs
     *
     * @param reason
     *  the specified reason to mark as resolved
     *
     * @throws IllegalArgumentException
     *  if either reasonClass or reason are null or empty
     *
     * @return
     *  true if Candlepin resumes normal operations as a result of a call to this method; false
     *  if Candlepin is still held in suspend mode for other reasons
     */
    public synchronized boolean resolveSuspendReason(String reasonClass, String reason) {
        if (reasonClass == null || reasonClass.isEmpty()) {
            throw new IllegalArgumentException("reason class is null or empty");
        }

        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("reason string is null or empty");
        }

        Map<String, ModeChangeReason> classReasons = this.reasons.get(reasonClass);
        ModeChangeReason resolved = classReasons != null ? classReasons.remove(reason) : null;
        boolean resumed = false;

        if (resolved != null) {
            log.info("Suspend reason resolved: {}", resolved);

            if (classReasons.isEmpty()) {
                this.reasons.remove(reasonClass);

                if (this.reasons.isEmpty()) {
                    resumed = true;

                    log.info("Candlepin is resuming normal mode");
                    this.notifyListeners(Mode.SUSPEND, Mode.NORMAL);
                }
            }
        }

        return resumed;
    }

    /**
     * Notifies all registered mode change listeners that the mode has changed.
     *
     * @param previousMode
     *  the previous mode
     *
     * @param currentMode
     *  the updated, current Candlepin mode
     */
    private void notifyListeners(Mode previousMode, Mode currentMode) {
        for (ModeChangeListener listener : this.listeners) {
            listener.handleModeChange(this, previousMode, currentMode);
        }
    }

    /**
     * Fetches the current mode.
     *
     * @return
     *  the current mode
     */
    public synchronized Mode getCurrentMode() {
        return this.reasons.size() > 0 ? Mode.SUSPEND : Mode.NORMAL;
    }

    /**
     * Fetches the reason details for the current mode. If the current mode does not have any
     * associated reasons, this method returns an empty collection.
     *
     * @return
     *  the reason details for the current mode
     */
    public synchronized Set<ModeChangeReason> getModeChangeReasons() {
        Set<ModeChangeReason> output = new HashSet<>();

        for (Map<String, ModeChangeReason> classReasons : this.reasons.values()) {
            output.addAll(classReasons.values());
        }

        return output;
    }

    /**
     * Registers the provided listener with this mode manager. If the listener is already
     * registered, this method does nothing.
     *
     * @param listener
     *  the mode change listener to register
     *
     * @throws IllegalArgumentException
     *  if listener is null
     *
     * @return
     *  true if the listener was registered successfully; false otherwise
     */
    public synchronized boolean registerModeChangeListener(ModeChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        return this.listeners.add(listener);
    }

    /**
     * Removes the provided listener from this mode manager. If the listener was not already
     * registered with this mode manager, this method does nothing.
     *
     * @throws IllegalArgumentException
     *  if listener is null
     *
     * @return
     *  true if the listener was removed successfully; false otherwise
     */
    public synchronized boolean removeModeChangeListener(ModeChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        return this.listeners.remove(listener);
    }
}
