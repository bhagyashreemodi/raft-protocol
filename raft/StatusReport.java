package raft;

import java.io.Serializable;

/**
 * The {@code StatusReport} class encapsulates the status information of a Raft node. This information
 * is used to communicate the node's current state back to the Controller, typically in response to
 * command execution or status query requests. It includes details such as the node's log index, current term,
 * leadership status, and the count of RPC calls made.
 */
public class StatusReport implements Serializable {

    /**
     * The last log index known to the node. This can be used to understand the progress of log replication.
     */
    public int index;

    /**
     * The current term of the node. This indicates the node's latest view of the cluster's term, which
     * is essential for understanding the cluster's election state.
     */
    public int term;

    /**
     * Indicates whether the node believes it is the leader. This is important for determining the cluster's
     * leadership configuration and for routing client requests correctly.
     */
    public boolean leader;

    /**
     * The count of RPC calls made by the node. This can be used for monitoring
     * and debugging purposes.
     */
    public int callCount;

    /**
     * Constructs a new {@code StatusReport} with specified details about the node's status.
     *
     * @param index the index of the last log entry
     * @param term the current term of the node
     * @param leader a flag indicating if the node is the leader
     * @param callCount the count of RPC calls made by the node
     */
    public StatusReport(int index, int term, boolean leader, int callCount) {
        this.index = index;
        this.term = term;
        this.leader = leader;
        this.callCount = callCount;
    }
}

