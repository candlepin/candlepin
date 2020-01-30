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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.resource.util.JobStateMapper;
import org.candlepin.resource.util.JobStateMapper.ExternalJobState;

import java.time.ZoneOffset;


/**
 * The AsyncJobStatusTranslator provides translation from AsyncJobStatus model objects to
 * AsyncJobStatusDTOs
 */
public class AsyncJobStatusTranslator implements ObjectTranslator<AsyncJobStatus, AsyncJobStatusDTO> {

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

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.created(source.getCreated() != null ?
                source.getCreated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .updated(source.getUpdated() != null ?
                source.getUpdated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .id(source.getId())
            .key(source.getJobKey())
            .name(source.getName())
            .group(source.getGroup())
            .origin(source.getOrigin())
            .executor(source.getExecutor())
            .principal(source.getPrincipalName())
            .state(source.getState() != null ? source.getState().name() : null)
            .previousState(source.getPreviousState() != null ?
                source.getPreviousState().name() : null)
            .startTime(source.getStartTime() != null ?
                source.getStartTime().toInstant().atOffset(ZoneOffset.UTC) : null)
            .endTime(source.getEndTime() != null ?
                source.getEndTime().toInstant().atOffset(ZoneOffset.UTC) : null)
            .attempts(source.getAttempts())
            .maxAttempts(source.getMaxAttempts())
            .statusPath(source.getId() != null ?
                String.format("/jobs/%s", source.getId()) : null)
            .resultData(source.getJobResult());

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
