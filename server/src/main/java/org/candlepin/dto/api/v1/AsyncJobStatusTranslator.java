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
import org.candlepin.util.Util;


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

        destination.id(source.getId())
            .key(source.getJobKey())
            .name(source.getName())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .group(source.getGroup())
            .origin(source.getOrigin())
            .executor(source.getExecutor())
            .principal(source.getPrincipalName())
            .state(stateToString(mapState(source.getState())))
            .previousState(stateToString(mapState(source.getPreviousState())))
            .startTime(Util.toDateTime(source.getStartTime()))
            .endTime(Util.toDateTime(source.getEndTime()))
            .attempts(source.getAttempts())
            .maxAttempts(source.getMaxAttempts())
            .statusPath(source.getId() != null ?
                String.format("/jobs/%s", source.getId()) : null)
            .resultData(source.getJobResult());

        return destination;
    }

    private String stateToString(PublicJobState state) {
        if (state == null) {
            return null;
        }
        return state.name();
    }

    public static PublicJobState mapState(JobState state) {
        if (state == null) {
            return null;
        }
        PublicJobState publicJobState;
        switch (state) {
            case FINISHED:
                publicJobState = PublicJobState.FINISHED;
                break;
            case CREATED:
            case QUEUED:
                publicJobState = PublicJobState.CREATED;
                break;
            case CANCELED:
                publicJobState = PublicJobState.CANCELED;
                break;
            case FAILED_WITH_RETRY:
            case RUNNING:
            case SCHEDULED:
            case WAITING:
                publicJobState = PublicJobState.RUNNING;
                break;
            case ABORTED:
            case FAILED:
            default:
                publicJobState = PublicJobState.FAILED;
                break;
        }
        return publicJobState;
    }

}
