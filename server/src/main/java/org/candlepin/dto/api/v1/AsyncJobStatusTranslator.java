/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.resource.util.JobStateMapper;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;



/**
 * The AsyncJobStatusTranslator provides translation from AsyncJobStatus model objects to
 * AsyncJobStatusDTOs
 */
public class AsyncJobStatusTranslator extends TimestampedEntityTranslator<AsyncJobStatus, AsyncJobStatusDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO translate(AsyncJobStatus source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO translate(ModelTranslator translator, AsyncJobStatus source) {
        return source != null ? this.populate(translator, source, new AsyncJobStatusDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO populate(AsyncJobStatus source, AsyncJobStatusDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncJobStatusDTO populate(ModelTranslator translator, AsyncJobStatus source,
        AsyncJobStatusDTO destination) {

        destination = super.populate(translator, source, destination);

        destination.setId(source.getId());
        destination.setJobKey(source.getJobKey());
        destination.setName(source.getName());
        destination.setGroup(source.getGroup());
        destination.setOrigin(source.getOrigin());
        destination.setExecutor(source.getExecutor());
        destination.setPrincipal(source.getPrincipalName());
        destination.setStartTime(source.getStartTime());
        destination.setEndTime(source.getEndTime());
        destination.setAttempts(source.getAttempts());
        destination.setMaxAttempts(source.getMaxAttempts());
        destination.setResult(source.getJobResult());
        destination.setState(translateState(source.getState()));
        destination.setPreviousState(translateState(source.getPreviousState()));

        return destination;
    }

    /**
     * Translates the internal job state into a string representing the name of the appropriate
     * external job state. If the internal job state is null, this function returns null.
     *
     * @param state
     *  the internal job state to translate
     *
     * @return
     *  a string representing the name of the translated job state, or null if no state was provided
     */
    private static String translateState(JobState state) {
        ExternalJobState translated = JobStateMapper.translateState(state);
        return translated != null ? translated.name() : null;
    }

}
