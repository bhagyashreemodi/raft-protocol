package raft;

import java.io.Serializable;

/**
 * Represents the result of an attempt to append entries to the log in the Raft consensus algorithm.
 * This class encapsulates the outcome of the append entries RPC, indicating both the term
 * of the log entry and whether the append operation was successful.
 */
public class AppendEntriesResult implements Serializable {
    /**
     * The term of the current log entry. This term helps nodes in the Raft cluster to keep
     * their logs consistent with each other. It represents the Raft term when the log entry
     * was created.
     */
    private final int term;

    /**
     * Indicates whether the log entry was appended successfully to the follower's log.
     * A value of true indicates success, while false indicates failure, which can occur
     * for various reasons such as log inconsistency or if the leader's term is outdated.
     */
    private final boolean success;

    /**
     * Constructs an {@code AppendEntriesResult} with the specified term and success flag.
     *
     * @param term the term of the log entry, indicating the Raft term at the time of the append operation
     * @param success the outcome of the append operation, where true indicates success and false indicates failure
     */
    public AppendEntriesResult(int term, boolean success) {
        this.term = term;
        this.success = success;
    }

    /**
     * Returns the term of the node that responded to the append entries RPC.
     *
     * @return The term of the responding node.
     */
    public int getTerm() {
        return term;
    }

    /**
     * Indicates whether the append entries operation was successful.
     *
     * @return True if the operation was successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }
}
