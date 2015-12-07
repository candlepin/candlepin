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
package org.candlepin.gutterball.util.cron;

import org.candlepin.gutterball.util.cron.matchers.CronComplexMatcher;
import org.candlepin.gutterball.util.cron.matchers.CronMatcher;
import org.candlepin.gutterball.util.cron.matchers.CronRangeMatcher;
import org.candlepin.gutterball.util.cron.matchers.CronStaticValueMatcher;
import org.candlepin.gutterball.util.cron.matchers.CronStepMatcher;
import org.candlepin.gutterball.util.cron.matchers.CronWildcard;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * The CronSchedule class represents a cron-like schedule parser and matcher. It accepts schedules
 * in a simplified cron format and can find dates matching the given schedule relative to other
 * dates.
 * <p/>
 *
 * The schedule format accepted by this class is as follows:
 * <pre>
 *           ┌───────────── minutes (0 - 59)
 *           │ ┌────────────── hour (0 - 23)
 *           │ │ ┌─────────────── day of month (1 - 31)
 *           │ │ │ ┌──────────────── month (1 - 12)
 *           │ │ │ │ ┌───────────────── day of week (0 - 6, representing Sunday to Saturday)
 *           │ │ │ │ │
 *           │ │ │ │ │
 * schedule: * * * * *
 * </pre>
 *
 * Unlike some, more robust, implementations, this class does not accept multiple values for Sunday,
 * nor does it accept named or abbreviated names for either months or days of the week.
 */
public class CronSchedule {
    public static final long MAX_TIME_UNTIL_OCCURRANCE = 315569520000L; // 10 years

    private CronMatcher minute;
    private CronMatcher hour;
    private CronMatcher dayOfMonth;
    private CronMatcher month;
    private CronMatcher dayOfWeek;

    public CronSchedule(String schedule) {
        if (schedule == null || schedule.isEmpty()) {
            throw new IllegalArgumentException("schedule is null or empty");
        }

        this.parseSchedule(schedule);
    }

    private void parseSchedule(String schedule) {
        Pattern parser = Pattern.compile("\\A(?:(\\*)(?:/(\\d+))?|(\\d+)(?:-(\\d+))?)(,(?=\\S+)|\\s+|\\z)");
        Matcher matcher = parser.matcher(schedule);
        matcher.region(0, schedule.length());

        this.minute = this.parseScheduleField(matcher, 0, 59);
        if (matcher.hitEnd()) {
            throw new RuntimeException("Unexpected end of schedule: missing hours");
        }

        this.hour = this.parseScheduleField(matcher, 0, 23);
        if (matcher.hitEnd()) {
            throw new RuntimeException("Unexpected end of schedule: missing day of month");
        }

        this.dayOfMonth = this.parseScheduleField(matcher, 1, 31);
        if (matcher.hitEnd()) {
            throw new RuntimeException("Unexpected end of schedule: missing month");
        }

        this.month = this.parseScheduleField(matcher, 1, 12);
        if (matcher.hitEnd()) {
            throw new RuntimeException("Unexpected end of schedule: missing day of week");
        }

        this.dayOfWeek = this.parseScheduleField(matcher, 0, 6);
        if (!matcher.hitEnd()) {
            throw new RuntimeException(
                "Expected end of schedule but found more data at offset " + matcher.end()
            );
        }
    }

    private CronMatcher mergeMatchers(CronMatcher existing, CronMatcher matcher) {
        CronMatcher output = matcher;

        if (existing != null) {
            if (existing instanceof CronComplexMatcher) {
                output = existing;
                ((CronComplexMatcher) output).add(matcher);
            }
            else {
                output = new CronComplexMatcher();
                ((CronComplexMatcher) output).add(existing);
                ((CronComplexMatcher) output).add(matcher);
            }
        }

        return output;
    }

    private CronMatcher parseScheduleField(Matcher matcher, int min, int max) {
        CronMatcher cmatcher = null;

        boolean skip = false;
        boolean done = false;

        try {
            while (!done) {
                if (!matcher.lookingAt()) {
                    // TODO: Improve this exception message
                    throw new RuntimeException(String.format(
                        "Invalid schedule format: %d-%d", matcher.regionStart(), matcher.regionEnd()
                    ));
                }

                if (!skip) {
                    // Figure out what we have...
                    if (matcher.group(1) != null) {
                        if (matcher.group(2) != null) {
                            int step = Integer.parseInt(matcher.group(2));

                            if (step == 0 || step < min || step > max) {
                                throw new RuntimeException(String.format(
                                    "invalid schedule step: %s is not a valid step for field range %d-%d",
                                    step, min, max
                                ));
                            }

                            cmatcher = this.mergeMatchers(cmatcher, new CronStepMatcher(step, min, max));
                        }
                        else {
                            cmatcher = new CronWildcard();

                            // Done processing this field, but we still need to validate the rest of the
                            // input
                            skip = true;
                        }
                    }
                    else {
                        int vlo = Integer.parseInt(matcher.group(3));

                        if (matcher.group(4) != null) {
                            int vhi = Integer.parseInt(matcher.group(4));

                            if (vlo < min || vlo > max || vhi < min || vhi > max || vlo > vhi) {
                                throw new RuntimeException(String.format(
                                    "invalid schedule range: %s-%s lies outside the field range of %d-%d",
                                    vlo, vhi, min, max
                                ));
                            }

                            cmatcher = this.mergeMatchers(cmatcher, new CronRangeMatcher(vlo, vhi));
                        }
                        else {
                            if (vlo < min || vlo > max) {
                                throw new RuntimeException(String.format(
                                    "invalid schedule value: %s lies outside the field range of %d-%d",
                                    vlo, min, max
                                ));
                            }

                            cmatcher = this.mergeMatchers(cmatcher, new CronStaticValueMatcher(vlo));
                        }
                    }
                }

                // Break if necessary...
                if (matcher.group(5) == null || !matcher.group(5).startsWith(",")) {
                    // End of input or end of section
                    done = true;
                }

                // Update our region
                matcher.region(matcher.end(), matcher.regionEnd());
            }
        }
        catch (NumberFormatException e) {
            // This can't actually happen, so long as our regex isn't broken
        }

        return cmatcher;
    }

    /**
     * Retrieves the next occurance matching this schedule
     *
     * @return
     *  The date representing the next occurance of this schedule
     */
    public Date getNextOccurance() {
        return this.getNextOccurance(new Date());
    }

    /**
     * Retrieves the next occurance matching this schedule, relative to the specified date.
     *
     * @param date
     *  The relative date from which to retrieve the next scheduled occurance
     *
     * @throws RuntimeException
     *  if the next occurrance would be more than 10 years away
     *
     * @return
     *  the date representing the next occurance of this schedule from the given date
     */
    public Date getNextOccurance(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        if (calendar.get(Calendar.SECOND) != 0) {
            calendar.set(Calendar.SECOND, 0);
            calendar.add(Calendar.MINUTE, 1);
        }

        int offset, current;
        long now = System.currentTimeMillis();

        while (true) {
            current = calendar.get(Calendar.MINUTE);

            calendar.set(Calendar.MINUTE, this.minute.next(current));
            offset = this.minute.hasNext(current) ? 0 : 1;

            current = calendar.get(Calendar.HOUR_OF_DAY) + offset;
            calendar.set(Calendar.HOUR_OF_DAY, this.hour.next(current));
            offset = this.hour.hasNext(current) ? 0 : 1;

            current = calendar.get(Calendar.DAY_OF_MONTH) + offset;
            calendar.set(Calendar.DAY_OF_MONTH, this.dayOfMonth.next(current));
            offset = this.dayOfMonth.hasNext(current) ? 1 : 2;

            current = calendar.get(Calendar.MONTH) + offset;
            calendar.set(Calendar.MONTH, this.month.next(current) - 1);

            if (!this.month.hasNext(current)) {
                calendar.add(Calendar.YEAR, 1);
            }

            if (!this.dayOfWeek.matches(calendar.get(Calendar.DAY_OF_WEEK) - 1)) {
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.add(Calendar.DAY_OF_MONTH, 1);

                // This sanity check should protect us from exceedingly silly or broken schedules
                // (such as February 31st)
                if (calendar.getTimeInMillis() - now > MAX_TIME_UNTIL_OCCURRANCE) {
                    throw new RuntimeException("Next occurrance is more than 10 years away");
                }

                continue;
            }

            break;
        }

        return calendar.getTime();
    }

}
