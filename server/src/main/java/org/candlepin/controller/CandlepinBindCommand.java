package org.candlepin.controller;

/**
 * Created by vrjain on 3/31/17.
 */
public interface CandlepinBindCommand {

    /* checks on data available if this command applies to the current request.
     *
     * For example: no need to enqueue HandleSelfCertificateCommand this is a
     * share consumer.
     */
    public boolean isApplicable();

    /* any pre-processing required before we enqueue. The intent is to leave
     * as little as possible to be executed when the command is actually executed, because
     * the pool would be locked at that time. This method may need some of the payload elements
     * of the command to be set, and may enrich the rest after computation.
     *
     * For example: compute the compliance hash so the consumer can be update later.
     */
    public void preProcess();


    /* actual execution of the command, at this point the pool has been locked,
     * we have all the pre-computation completed, so this should be mostly assigning references
     * and saves.
     *
     * For example: update consumer compliance hash and persist the consumer.
     */
    public void execute();

}
