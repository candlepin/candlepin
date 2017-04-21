package org.candlepin.controller;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by vrjain on 3/31/17.
 */
public class CandlepinCommandQueue {

    Queue<CandlepinBindCommand> queue = new LinkedList<CandlepinBindCommand>();

    public void enqueue(CandlepinBindCommand command) {
        queue.add(command);
    }

    public void execute() {
        Iterator<CandlepinBindCommand> iterator = queue.iterator();
        while(iterator.hasNext()) {
            iterator.next().execute();
        }
    }

}
