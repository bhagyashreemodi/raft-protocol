package raft;

import java.io.Serializable;

/**
 * Represents the result of a vote request in the Raft consensus algorithm.
 * This class encapsulates the information about a vote that was requested during
 * the election process in Raft. It contains details about the term of the election
 * and whether the vote was granted.
 */
public class VoteResult implements Serializable {

    /**
     * The term of the election for which the vote was requested.
     * The term is a monotonically increasing value that represents the Raft cluster's
     * timeline, ensuring the consistency and correctness of the leader election process.
     */
    private final int term;

    /**
     * Indicates whether the vote was granted for the term. A value of {@code true}
     * means that the vote was granted, while {@code false} indicates that the vote
     * was not granted. The vote could be denied for various reasons, such as if there's
     * already a leader for the term or if the candidate's log is not up-to-date.
     */
    private final boolean voteGranted;

    /**
     * Constructs a {@code VoteResult} with the specified term and the vote granted status.
     *
     * @param term the term of the election for which the vote is being reported
     * @param voteGranted the status of the vote, where true indicates that the vote was granted
     */
    public VoteResult(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    /**
     * Returns the term of the election for which the vote was requested.
     *
     * @return the term of the election
     */
    public int getTerm() {
        return term;
    }

    /**
     * Indicates whether the vote was granted for the requested term.
     *
     * @return {@code true} if the vote was granted, {@code false} otherwise
     */
    public boolean isVoteGranted() {
        return voteGranted;
    }
}
