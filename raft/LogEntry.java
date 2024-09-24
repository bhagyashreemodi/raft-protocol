package raft;

import java.io.Serializable;

/**
 * Represents a log entry in the Raft distributed consensus algorithm.
 * Each log entry contains a command to be executed by the replicated state machines,
 * the term when the entry was received by the leader, and the index of the log entry
 * in the log. The term and index together uniquely identify a log entry.
 */
public class LogEntry implements Serializable {

    /**
     * The term in which this log entry was created. The term is a monotonically increasing number
     * that represents the current epoch of the Raft cluster leadership, ensuring the correctness
     * of the leader election process.
     */
    private final int term;

    /**
     * The command to be executed by the state machine. In Raft, commands are encapsulated in log
     * entries and replicated across the cluster to ensure consistency. The nature of the command
     * depends on the application using Raft.
     */
    private final int command;

    /**
     * The index of this log entry in the log. The index is a unique identifier for log entries and
     * is used to keep them in a sequential, ordered manner which is crucial for the correctness of
     * the Raft algorithm.
     */
    private final int index;

    /**
     * Constructs a new log entry with the given term, command, and index.
     *
     * @param term The term during which the log entry was received by the leader.
     * @param command The command that the log entry contains, to be executed by the state machine.
     * @param index The index of the log entry in the log.
     */
    public LogEntry(int term, int command, int index) {
        this.term = term;
        this.command = command;
        this.index = index;
    }

    /**
     * Returns the term of this log entry.
     *
     * @return The term of the log entry.
     */
    public int getTerm() {
        return term;
    }

    /**
     * Returns the command contained in this log entry.
     *
     * @return The command to be executed by the state machine.
     */
    public int getCommand() {
        return command;
    }


    /**
     * Returns the index of this log entry in the log.
     *
     * @return The index of the log entry.
     */
    public int getIndex() {
        return index;
    }
}
