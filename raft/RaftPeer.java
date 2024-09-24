package raft;

import remote.RemoteObjectException;
import remote.Service;
import remote.StubFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RaftPeer class represents a Raft peer node that interacts with others using remote calls
 *  -- each RaftPeer supports a Service interface to accept incoming remote calls per RaftInterface
 *  -- each RaftPeer holds a list of stub interfaces to request remote calls per RaftInferface
 *  -- all remote calls are implemented using the underlying remote object library
 */
public class RaftPeer implements RaftInterface {
    /**
     * Constructor for RaftPeer
     * port numbers are assigned sequentially to peers from id = 0 to id = num-1, so any peer
     * can determine the port numbers of other peers from the give parameters
     */

    // Raft configuration parameters
    private final Logger logger = LogUtil.getLogger(RaftPeer.class.getName());
    /**
     * The port number this Raft peer listens on.
     */
    private final int port;
    /**
     * The unique identifier for this Raft peer.
     */

    private final int id;
    /**
     * The total number of peers in the Raft cluster.
     */
    private final int numPeers;
    /**
     * The service abstraction for network communication.
     */
    private final Service<RaftInterface> service;
    /**
     * An array of stubs for communication with other Raft peers.
     */
    private final RaftInterface[] peerStubs;
    /**
     * Indicates whether the Raft peer is alive and participating in the cluster.
     */
    private final AtomicBoolean alive = new AtomicBoolean(false);

    // Raft state variables
    /**
     * The current term of this Raft peer.
     */
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    /**
     * A synchronization lock for thread-safe operations.
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * An array tracking the next log entry to send to each peer.
     */
    private final int[] nextIndex;
    /**
     * An array tracking the highest log entry known to be replicated on each peer.
     */
    private final int[] matchIndex;
    /**
     * The index of the last log entry.
     */
    private int lastLogIndex = 0;
    /**
     * The peer ID that this Raft peer has voted for in the current term.
     */
    private final AtomicInteger votedFor = new AtomicInteger(-1); // Track who this peer voted for

    // Election timeout configuration
    /**
     * A random number generator for election timing and other randomness.
     */
    private final Random random = new Random();
    /**
     * The configured election timeout duration for this peer.
     */
    private long electionTimeout = 500 + random.nextInt(100, 300);
    /**
     * The timestamp of the last heartbeat message received.
     */
    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    /**
     * The time at which the current election started.
     */
    private volatile long electionStartTime = System.currentTimeMillis();

    // Election state
    /**
     * The number of votes this Raft peer has received in the current election.
     */
    private final AtomicInteger votes = new AtomicInteger(0);
    /**
     * A flag to stop the election process.
     */
    private AtomicBoolean stopElection = new AtomicBoolean(false);

    /**
     * RaftPeer states
     * <p>
     *     FOLLOWER: the peer is in the follower state
     *     CANDIDATE: the peer is in the candidate state
     *     LEADER: the peer is in the leader state
     */
    private enum State { FOLLOWER, CANDIDATE, LEADER }
    /**
     * The current state of this Raft peer (follower, candidate, or leader).
     */
    private volatile State state = State.FOLLOWER;

    // Background tasks
    /**
     * The thread responsible for handling election timeouts.
     */
    private Thread electionTimer;
    /**
     * The thread responsible for sending heartbeat messages.
     */
    private Thread heartBeatSender;

    // Log Variables
    /**
     * The list of log entries for this Raft peer.
     */
    private final List<LogEntry> logs = new ArrayList<>();
    /**
     * The index of the highest log entry known to be committed.
     */
    private volatile int commitIndex = 0;


    /**
     * Constructor for RaftPeer
     *
     * @param port    peer's service port number
     * @param id     peer's id/index among peers
     * @param num    number of peers in the system
     * <p>
     * port numbers are assigned sequentially to peers from id = 0 to id = num-1, so any peer
     * can determine the port numbers of other peers from the give parameters
     */
    public RaftPeer(int port, int id, int num) {
        // when a new Raft peer is created, its initial state should be populated into suitable object
        // member variables, and its remote Service and StubFactory components should be created,
        // but the Service should not be started (the Controller will do that when ready).
        //
        // the remote Service should be bound to the given port number.  each stub created by the
        // StubFactory will be used to interact with a different Raft peer, and different port numbers
        // are used for each Raft peer.  the Controller assigns these port numbers sequentially, starting
        // from peer with `id = 0` and ending with `id = num-1`, so any peer who knows its own
        // `id`, `port`, and `num` can determine the port number used by any other peer.
        logger.log(Level.INFO,"Creating RaftPeer on port " + port + " with id " + id + " and num " + num);
        this.port = port;
        this.id = id;
        this.numPeers = num;

        // Initialize the Service with RaftInterface class, this instance, and the port number
        this.service = new Service<>(RaftInterface.class, this, port);

        // Initialize stubs for interacting with other peers
        this.peerStubs = new RaftInterface[numPeers];
        for (int i = 0; i < num; i++) {
            if (i != id) {
                int peerPort = calculatePeerPort(i);
                String address = "localhost:" + peerPort; // Using localhost and peerPort for the address
                peerStubs[i] = StubFactory.create(RaftInterface.class, address);
            }
        }
        this.votedFor.set(-1);
        this.currentTerm.set(0);
        this.nextIndex = new int[numPeers];
        this.matchIndex = new int[numPeers];
        this.resetLeaderState();
    }


    /**
     * Resets the leader state for this Raft peer.
     * <p>
     * This method is used to reset the leader's index tracking for each peer in the cluster. It sets each peer's nextIndex to the
     * current log size plus one, indicating the next log entry to send to that peer, and resets each peer's matchIndex to 0, indicating
     * the highest log entry known to be replicated on that peer. This method is typically called during a new leader election or when
     * the leadership changes.
     * </p>
     */
    private void resetLeaderState() {
        lock.lock();
        try {
            for (int i = 0; i < numPeers; i++) {
                nextIndex[i] = logs.size() + 1;
                matchIndex[i] = 0;
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Calculates the port number for a given peer ID.
     * <p>
     * This method computes the port number used to communicate with a peer based on its ID. It utilizes the initial port number and ID
     * of this peer, adjusting them to match the target peer's port. This calculation ensures that each peer can dynamically determine the
     * port numbers for communicating with all other peers in the cluster, assuming sequential port assignments starting from the initial
     * peer (ID 0).
     * </p>
     *
     * @param peerId the ID of the peer for which the port number is calculated.
     * @return the port number for the specified peer.
     */
    private int calculatePeerPort(int peerId) {
        return (this.port - this.id) + peerId;
    }

    /**
     * Activates the Raft peer if it is not already active. This involves setting the peer's alive status to true,
     * starting the service associated with the peer, recording the current time as the last heartbeat time,
     * and ensuring that the election timer is running. If the election timer is not alive, it starts a new election
     * timer thread. Logs the activation event.
     * This method is designed to be called when a Raft peer needs to be brought up from an inactive state,
     * ensuring it begins participating in the Raft protocol processes, such as elections and log replication.
     */
    public void Activate() {
        try {
            if(!alive.get()) {
                alive.set(true);
                this.service.start();
                lastHeartbeatTime = System.currentTimeMillis();
                if (electionTimer == null || !electionTimer.isAlive()) {
                    electionTimer = new Thread(this::startElectionTimer);
                    electionTimer.start();
                }
                logger.log(Level.INFO,"RaftPeer activated on port " + this.port);
            }
        } catch (RemoteObjectException e) {
            logger.log(Level.SEVERE,"Failed to start the service: ", e);
        }
    }


    /**
     * Ensures the initiation and continuous sending of heartbeat messages to other peers in the cluster.
     * <p>
     * This method checks if the current Raft peer is the leader and, if so, starts a background thread (if not already running)
     * dedicated to sending heartbeat messages. These heartbeats are crucial for maintaining the leader's authority and preventing
     * unnecessary elections in a stable cluster. The method dynamically calculates the previous log index and term to include
     * in heartbeat messages, ensuring peers are up-to-date or can request missing log entries.
     * </p>
     * <p>
     * The heartbeat sending thread operates as long as the peer remains alive, is the leader, and the thread is not interrupted.
     * It sends heartbeats at regular intervals and ensures any active child threads for individual heartbeat messages are
     * properly managed and terminated if necessary. This method plays a critical role in the Raft consensus algorithm by
     * facilitating consistent state and leadership across the cluster.
     * </p>
     */
    private void checkAndStartHeartbeats() {
        try {
            if (heartBeatSender == null || !heartBeatSender.isAlive()) {
                heartBeatSender = new Thread(() -> {
                    Thread[] heartBeatThreads;
                    while (alive.get() && state == State.LEADER && !Thread.currentThread().isInterrupted()) {
                        final int prevLogIndex, prevLogTerm;
                        if (!this.logs.isEmpty()) {
                            LogEntry lastLog = this.logs.get(this.logs.size() - 1);
                            prevLogIndex = lastLog.getIndex();
                            prevLogTerm = lastLog.getTerm();
                        } else {
                            prevLogIndex = 0;
                            prevLogTerm = 0;
                        }
                        heartBeatThreads = sendHeartbeats(prevLogIndex, prevLogTerm);
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            logger.log(Level.INFO,"Heartbeat sender interrupted on port " + this.port + " with id " + this.id);
                            Thread.currentThread().interrupt(); // Optional: re-interrupt the thread if you catch the InterruptedException
                            break;
                        } finally {
                            for (Thread thread : heartBeatThreads) {
                                if (thread != null && thread.isAlive()) {
                                    thread.interrupt();
                                }
                            }
                        }
                    }
                });
                heartBeatSender.start();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,"Failed to start heartbeats: ", e);
        }

    }

    /**
     * Deactivates the Raft peer, ensuring it stops accepting remote calls and ceases its participation
     * in Raft protocol processes such as elections and heartbeats. This method performs several key actions:
     * logs the intention to deactivate, stops the service associated with the peer, sets the peer's alive
     * status to false, interrupts and joins the heartbeat sender and election timer threads (if they are running),
     * and logs the completion of the deactivation process.
     *
     * This method is designed to be called when the Raft peer needs to be gracefully shut down or temporarily
     * removed from the cluster's operations, allowing for a clean transition out of the active state.
     */
    public void Deactivate() {
        try {
            // Stop the Service to no longer accept remote calls.
            logger.log(Level.INFO,"Deactivating the RaftPeer on port with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex );

            this.service.stop();
            alive.set(false);
            // Safely interrupt the election timer if it's a separate thread

            if (heartBeatSender != null) {
                heartBeatSender.interrupt();
                heartBeatSender.join();
                heartBeatSender = null;
            }

            if (electionTimer != null) {
                electionTimer.interrupt();
                electionTimer.join();
                electionTimer = null;
            }

            logger.log(Level.INFO,"RaftPeer deactivated on port " + this.port);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE,"Failed to deactivate the RaftPeer: " + e.getMessage());
        }
    }



    /**
     * Handles a vote request from a candidate peer in the election process.
     * <p>
     * This method processes a vote request according to the Raft election protocol. It evaluates the candidate's term, log index,
     * and log term against its own to decide whether to grant or deny the vote. Votes are granted based on the recency of the
     * candidate's log and the voter's current state, including its current term and whether it has already voted in the current term.
     * </p>
     *
     * @param term         the candidate's current term
     * @param candidateId  the requesting candidate's ID
     * @param lastLogIndex the index of the candidate's last log entry
     * @param lastLogTerm  the term of the candidate's last log entry
     * @return a {@link VoteResult} object containing the current term and a boolean indicating whether the vote was granted.
     * @throws RemoteObjectException if a remote operation fails
     */
    @Override
    public VoteResult RequestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm) throws RemoteObjectException {
        try {
            if(term < currentTerm.get()) {
                return new VoteResult(currentTerm.get(), false);
            }
            if (term == currentTerm.get()) {
                if (votedFor.get() != -1 && votedFor.get() != candidateId) {
                    System.out.println(this.id + " already voted for " + votedFor + " at term " + term);
                    return new VoteResult(currentTerm.get(), false);
                }
            }
            if(term > currentTerm.get()) {
                currentTerm.set(term);
                votedFor.set(-1);
                state = State.FOLLOWER;
            }
            boolean isCandidateLogUpToDate = false;
            int lastEntryTerm = logs.isEmpty() ? 0 : logs.get(logs.size() - 1).getTerm();
            if (lastLogTerm > lastEntryTerm || (lastLogTerm == lastEntryTerm && lastLogIndex >= logs.size())) {
                isCandidateLogUpToDate = true;
            }

            if (isCandidateLogUpToDate) {
                // Case 2: If votedFor is null or candidateId, and candidate's log is at least as up-to-date as receiver's log, grant vote
                this.votedFor.set(candidateId);
                //this.currentTerm.set(term); // Update current term to the candidate's term
                return new VoteResult(this.currentTerm.get(), true);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE,"Failed to request vote: ", e);
        }
        return new VoteResult(this.currentTerm.get(), false);
    }

    /**
     * Handles an append entries request from the leader. This method is used both for replicating log entries and as a heartbeat mechanism.
     * <p>
     * This method processes the append entries RPC from the leader. It checks the term to ensure the request comes from a current leader
     * and verifies the log consistency before appending any new entries. If the term of the request is greater than the current term of
     * this peer, it updates its term and transitions to the follower state. This method also handles updating the peer's log based on the
     * entries provided in the RPC, ensuring that only new and consistent entries are appended. Finally, it updates the commit index if the
     * leader's commit index is greater than the current commit index of this peer.
     * </p>
     * <p>
     * The method returns a result indicating whether the append operation was successful and the current term of this peer, which helps
     * the leader maintain an up-to-date view of the state of each peer in the cluster.
     * </p>
     *
     * @param term         the leader's current term
     * @param leaderId     the ID of the leader sending this append entry request
     * @param prevLogIndex the index of the log entry immediately preceding the new ones
     * @param prevLogTerm  the term of the log entry immediately preceding the new ones
     * @param entries      the list of log entries to append to the local log
     * @param leaderCommit the leader's commit index
     * @return an {@link AppendEntriesResult} object containing the current term of this peer and a boolean indicating whether the append was successful.
     * @throws RemoteObjectException if a remote operation fails
     */
    @Override
    public AppendEntriesResult AppendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm, List<LogEntry> entries, int leaderCommit) throws RemoteObjectException {
        try {
           if (term < currentTerm.get()) {
                return new AppendEntriesResult(currentTerm.get(), false);
            }
            //Update the state as received AppendEntries from leader
            lastHeartbeatTime = System.currentTimeMillis();
            state = State.FOLLOWER;
            votes.set(0);
            // If the term in the RPC is greater than the peer's current term, then the peer
            // recognizes the leader as legitimate and updates its term to the RPC's term.
            // update current term and leader info
            if(term > currentTerm.get()) {
                currentTerm.set(term);
                votedFor.set(-1);
            }

            // check log consistency
            if (prevLogIndex > 0 && (prevLogIndex > logs.size() || logs.get(prevLogIndex-1).getTerm() != prevLogTerm)) {
                return new AppendEntriesResult(this.currentTerm.get(), false);
            }

            // Append new entries not already in the log
            for (LogEntry entry : entries) {
                logger.log(Level.INFO,"Appending entry " + entry.getIndex() + " to logs on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex);
                if (entry.getIndex() > logs.size()) {
                    logs.add(entry);
                } else {
                    if (entry.getTerm() != logs.get(entry.getIndex() - 1).getTerm()) {
                        while (logs.size() >= entry.getIndex()) {
                            logs.remove(entry.getIndex() - 1);
                        }
                        logs.add(entry);
                    }
                }
            }

            if (!logs.isEmpty()) {
                lastLogIndex = logs.get(logs.size() - 1).getIndex();
            }

            // Update commitIndex
            if (leaderCommit > commitIndex) {
                int lastEntryIndex = logs.get(logs.size() - 1).getIndex();
                commitIndex = Math.min(leaderCommit, lastEntryIndex);
            }

            return new AppendEntriesResult(this.currentTerm.get(), true);


        } catch (Exception e) {
            logger.log(Level.SEVERE,"Failed to append entries: ", e);
            return new AppendEntriesResult(this.currentTerm.get(), false);
        }
    }

    /**
     * Retrieves the command of the log entry at the specified index if it has been committed.
     * <p>
     * This method is used to fetch the command stored in a committed log entry based on its index. It checks if the requested index is
     * within the bounds of committed entries and returns the command if it exists. If the index is out of range or refers to an uncommitted
     * entry, the method returns 0 to indicate the absence of a valid command. This operation helps ensure that only committed commands are
     * executed or queried, maintaining the consistency and integrity of the state machine.
     * </p>
     *
     * @param index the index of the log entry whose command is to be retrieved.
     * @return the command of the specified log entry if committed, or 0 if the index is invalid or refers to an uncommitted entry.
     * @throws RemoteObjectException if a remote operation fails.
     */
    @Override
    public int GetCommittedCmd(int index) throws RemoteObjectException {
        logger.log(Level.INFO,"GetCommittedCmd on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex + " and last term "  + " and voted for " + this.votedFor.get() + " and votes " + this.votes.get());
        logger.log(Level.INFO, "required index: " + index + " and commit index: " + commitIndex + " and logs size: " + logs.size() + " on peer " + this.id);
        if (index > commitIndex || index > logs.size() || index <= 0) {
            return 0;
        }
        return logs.get(index - 1).getCommand();
    }

    /**
     * Retrieves the current status of the Raft peer, including its last log index, current term, leadership status, and the number of
     * operations processed.
     * <p>
     * This method provides a snapshot of the peer's status, which includes critical information such as its last log index, current term,
     * whether it is the leader, and the count of operations it has processed. This information is crucial for monitoring and debugging
     * purposes, allowing for an understanding of the peer's role in the cluster and its activity level. The method returns a default status
     * report indicating the peer is deactivated if it is not alive.
     * </p>
     *
     * @return a {@link StatusReport} object containing the peer's last log index, current term, leadership status, and operation count.
     * @throws RemoteObjectException if a remote operation fails.
     */
    @Override
    public StatusReport GetStatus() throws RemoteObjectException {
        //lock.lock();
        try {
            if(!alive.get()) {
                logger.log(Level.INFO,"GetStatus called on a deactivated peer");
                return new StatusReport(-1, -1, false, 0);
            }
            logger.log(Level.INFO,"GetStatus on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex );
            return new StatusReport(lastLogIndex, this.currentTerm.get(), this.state == State.LEADER, service.getCount());
        } finally {
            //lock.unlock();
        }
    }

    /**
     * Accepts a new command for the Raft cluster, to be appended to the log and eventually committed.
     * <p>
     * This method is responsible for handling new commands when the current peer is the leader. It appends a new log entry with the
     * command to the log and attempts to replicate this entry to the follower peers. The method ensures that new commands are only
     * accepted by the leader, throwing an exception if called on a follower or candidate.
     * </p>
     * <p>
     * After appending the command, the method triggers log entry replication to followers and returns the current status of the leader,
     * including the latest log index and term.
     * </p>
     *
     * @param command the command to be added to the log.
     * @return a {@link StatusReport} indicating the leader's status after attempting to append and replicate the new command.
     * @throws RemoteObjectException if a remote operation fails or if the method is called on a non-leader peer.
     */
    @Override
    public StatusReport NewCommand(int command) throws RemoteObjectException {

        try {
            logger.log(Level.INFO,"New command received on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex);
            if (state != State.LEADER) {
                // If not the leader, cannot accept new commands.
                throw new RemoteObjectException("Not the leader");
            }
            LogEntry newEntry = new LogEntry(currentTerm.get(), command, logs.size() + 1);
            logs.add(newEntry);
            lastLogIndex = logs.get(logs.size() - 1).getIndex();
            logger.log(Level.INFO,"New command added to logs on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"Failed to add new command: ", e);
        }

        // Replicate the new entry to followers.
        replicateEntries();


        return GetStatus();
    }

    /**
     * Initiates the replication of new log entries to all follower peers.
     * <p>
     * This private helper method is called by the leader after a new command is added to its log. It creates and starts threads for each
     * follower to send the AppendEntries RPC, ensuring the new log entries are replicated across the cluster. This method plays a crucial
     * role in maintaining log consistency and ensuring that all committed entries are eventually replicated to all peers.
     * </p>
     * <p>
     * The method waits for all replication threads to complete before returning, allowing the leader to subsequently check if the new
     * entries have been sufficiently replicated for them to be committed.
     * </p>
     */
    private void replicateEntries() {
        Thread[] replicationThreads = new Thread[numPeers];
        for (int i = 0; i < numPeers; i++) {
            if (i != this.id) { // Avoid sending AppendEntries to itself.
                final int peerId = i;
                logger.log(Level.INFO,"Replicating entries to peer " + peerId);
                Thread thread = new Thread(() -> sendAppendEntries(peerId));
                replicationThreads[i] = thread;
                thread.start();
            }
            else {
                logger.log(Level.INFO,"Skipping replication to self");
                matchIndex[i] = logs.size();
                nextIndex[i] = logs.size() + 1;

            }
        }
        for (Thread thread : replicationThreads) {

            try {
                if (thread != null) {
                    thread.join();
                }
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE,"Failed to join replication threads: ",e);
            }
        }
    }

    /**
     * Sends AppendEntries RPCs to a specific peer to replicate log entries.
     * <p>
     * This internal method is used by the leader to replicate its log entries to a follower identified by `peerId`. It handles the
     * logic for determining which log entries need to be sent based on the follower's progress and retries in case of failures. The
     * method adjusts the nextIndex and matchIndex for the follower based on the success or failure of the AppendEntries RPCs.
     * </p>
     *
     * @param peerId the ID of the peer to which log entries are being replicated.
     */
    private void sendAppendEntries(int peerId) {
        boolean success = false;
        int maxRetry = 5;
        while (!success && maxRetry > 0 && alive.get() && state == State.LEADER) {
            try {
                logger.log(Level.INFO,"Sending AppendEntries to peer " + peerId);
                List<LogEntry> entries;
                if (nextIndex[peerId] == 0) {
                    entries = new ArrayList<>(logs.subList(0, logs.size()));
                } else {
                    entries = new ArrayList<>(logs.subList(nextIndex[peerId] - 1, logs.size()));
                }
                int prevLogIndex = nextIndex[peerId] - 1;
                int prevLogTerm = -1;
                if (prevLogIndex > 0) {
                    prevLogTerm = logs.get(prevLogIndex - 1).getTerm();
                }
                AppendEntriesResult result = peerStubs[peerId].AppendEntries(this.currentTerm.get(), this.id, prevLogIndex, prevLogTerm, entries, commitIndex);
                logger.log(Level.INFO,"Received AppendEntries result from peer " + peerId + " with success " + result.isSuccess());
                success = result.isSuccess();
                lock.lock();
                try {
                    if (!success) {
                        if (nextIndex[peerId] > 0) {
                            nextIndex[peerId]--;
                            matchIndex[peerId] = matchIndex[peerId] > 0 ? matchIndex[peerId]-- : 0;
                        } else if (nextIndex[peerId] == 0) {
                            break;
                        }
                    } else {
                        matchIndex[peerId] = logs.size();
                        nextIndex[peerId] = logs.size() + 1;
                    }
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                maxRetry--;
                logger.log(Level.SEVERE,"Failed to send AppendEntries to peer " + peerId + ": ", e);
            }
        }
        updateCommitIndex();
    }

    /**
     * Updates the commit index after successfully replicating log entries to a majority of peers.
     * <p>
     * This method evaluates the replication progress (matchIndex) for each peer and updates the commit index of the leader if it
     * finds that a majority of peers have replicated entries up to a new index. This ensures that commands are committed in a
     * safe manner, respecting the Raft commitment rule.
     * </p>
     */
    private void updateCommitIndex() {
        lock.lock();
        try {
            logger.log(Level.INFO,"Updating commit index on port " + this.port + " with id " + this.id + " and state " + this.state + " and term " + this.currentTerm.get() + " and last index " + this.lastLogIndex);
            int maxMatch = -1;
            for (int i = 0; i < numPeers; ++i) {
                maxMatch = Math.max(maxMatch, matchIndex[i]);
            }

            for (int N = maxMatch; N > commitIndex; --N) {
                int numMatch = 0;
                for (int i = 0; i < numPeers; ++i) {
                    if (matchIndex[i] >= N) {
                        numMatch++;
                    }
                }
                if (numMatch > numPeers / 2 && logs.get(N - 1).getTerm() == this.currentTerm.get()) {
                    commitIndex = N;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Starts the election timer that triggers a new election if it expires without receiving heartbeats from the leader.
     * <p>
     * This method runs in a loop, constantly resetting the election timer to a random value within a specified range. If the timer
     * expires (i.e., no heartbeat is received within the timeout period), and this node is not already a leader, it initiates an
     * election by transitioning to the candidate state and starting the vote request process.
     * </p>
     */
    private void startElectionTimer() {
        try {
            while (alive.get() && !Thread.currentThread().isInterrupted()) {
                electionTimeout = generateRandomElectionTimeout();
                Thread.sleep(electionTimeout);
                long currentTime = System.currentTimeMillis();
                if ((currentTime - lastHeartbeatTime) > electionTimeout && this.state != State.LEADER) {
                    electionStartTime = System.currentTimeMillis();
                    startElection();
                }
            }
        } catch (InterruptedException e) {
            logger.log(Level.INFO,"Election timer interrupted on port " + this.port + " with id " + this.id);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates a new election round.
     * <p>
     * This method is called when the election timer expires without receiving a heartbeat. It increments the current term, votes for
     * itself, and transitions to the candidate state. Subsequently, it sends RequestVote RPCs to all other peers in the cluster to
     * gather votes.
     * </p>
     */
    public void startElection() {
        lock.lock();
        try {
            if (this.state != State.LEADER) { // Only start election if not already a leader
                currentTerm.incrementAndGet(); // Increment current term
                votedFor.set(id); // Vote for itself
                votes.set(1);
                this.state = State.CANDIDATE;
            }
        } finally {
            lock.unlock();
        }
        requestVotes();
    }

    /**
     * Sends vote requests to all other nodes in the cluster.
     * <p>
     * This method is invoked to request votes from all other peers during an election. It creates and starts separate threads for each
     * vote request to ensure that vote solicitation happens in parallel, allowing the node to potentially gather votes more quickly.
     * </p>
     */
    private void requestVotes() {
        int peerId = 0;
        List<Thread> voteRequestThreads = new ArrayList<>();
        while (peerId < numPeers) {
            if (peerId != this.id) {
                int finalPeerId = peerId;
                Thread voteRequestThread = new Thread(() -> sendVoteRequests(finalPeerId));
                voteRequestThreads.add(voteRequestThread);
                voteRequestThread.start();
            }
            peerId++;
        }
        while (!stopElection.get() && (System.currentTimeMillis() - electionStartTime) < electionTimeout){
            // Wait for election to complete for the duration of the election timeout
        }
        if (!stopElection.get()) {
            lock.lock();
            try {
                logger.log(Level.INFO,"Election timed out on port " + this.port + " with id " + this.id);
                votes.set(0);
                votedFor.set(-1);
                this.state = State.FOLLOWER;
                lastHeartbeatTime = System.currentTimeMillis();
            } finally {
                lock.unlock();
            }
            try {
                for (Thread voteRequestThread : voteRequestThreads) {
                    voteRequestThread.interrupt();
                    voteRequestThread.join();
                }
                voteRequestThreads.clear();
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE,"Failed to interrupt vote request threads: ",e);
            }
        }

    }

    /**
     * Starts threads to request votes from all peers except self.
     */
    private void sendVoteRequests(int peerId) {
        VoteResult voteResult = requestVote(peerId);
        if (voteResult != null) {

            logger.log(Level.INFO,"Vote result from peer " + peerId + ": " + voteResult.isVoteGranted());
            try {
                if (voteResult.isVoteGranted()) {
                    // Increment vote count atomically
                    if (votes.incrementAndGet() > numPeers / 2) {
                        lock.lock();
                        try {
                            stopElection.set(true);
                            this.state = State.LEADER;
                            logger.log(Level.INFO,this.id + " becomes the leader");
                            resetLeaderState();
                            checkAndStartHeartbeats();
                        } finally {
                            lock.unlock();
                        }
                    }
                    logger.log(Level.INFO,"Votes received: " + votes.get());
                } else if (voteResult.getTerm() > currentTerm.get()) {
                    lock.lock();
                    try {
                        logger.log(Level.INFO,"Vote term from peer " + peerId + ": " + voteResult.getTerm());
                        currentTerm.set(voteResult.getTerm());
                        this.state = State.FOLLOWER;
                        votes.set(0);
                        votedFor.set(-1);
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE,"Failed to send vote request to peer " + peerId, e);
            }


        }

    }

    /**
     * Sends heartbeat messages to all other peers in the cluster.
     * <p>
     * This method is utilized by the leader to maintain its authority and prevent followers from starting new elections. It sends
     * an AppendEntries RPC with no log entries (empty list) to each peer, which serves as a heartbeat. The method handles the
     * response from each peer to possibly update the leader's term and step down if a higher term is discovered. This is critical
     * for maintaining the health of the cluster and ensuring only one leader exists per term.
     * </p>
     *
     * @param prevLogIndex the index of the log entry immediately preceding the new one.
     * @param prevLogTerm  the term of the log entry immediately preceding the new one.
     * @return an array of threads, each responsible for sending heartbeats to a different peer.
     */
    private Thread[] sendHeartbeats(int prevLogIndex, int prevLogTerm) {
        int peerId = 0;
        Thread[] heartbeatThreads = new Thread[numPeers];
        while (peerId < numPeers) {
            if (peerId != this.id) {
                int finalPeerId = peerId;
                Thread heartbeatThread = new Thread(() -> {
                    try {
                        AppendEntriesResult result = peerStubs[finalPeerId].AppendEntries(currentTerm.get(), id, prevLogIndex, prevLogTerm, Collections.emptyList(), commitIndex);
                        if(result.getTerm() > currentTerm.get()) {
                            lock.lock();
                            try {
                                currentTerm.set(result.getTerm());
                                this.state = State.FOLLOWER;
                                votes.set(0);
                                votedFor.set(-1);
                            } finally {
                                lock.unlock();
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE,"Failed to send heartbeat to peer " + finalPeerId, e);
                    }
                });
                heartbeatThreads[peerId] = heartbeatThread;
                heartbeatThread.start();
            }
            peerId++;
        }
        return heartbeatThreads;
    }

    /**
     * Sends vote requests to all other nodes in the cluster.
     * <p>
     * This method is invoked to request votes from all other peers during an election. It creates and starts separate threads for each
     * vote request to ensure that vote solicitation happens in parallel, allowing the node to potentially gather votes more quickly.
     * </p>
     */
    private VoteResult requestVote(int peerId) {
        try {
            // Assume calculatePeerPort and peerStubs initialization done in the constructor
            int lastLogTerm = this.logs.isEmpty() ? 0 : this.logs.get(this.logs.size() - 1).getTerm();
            int lastLogIndex = this.logs.size();
            return peerStubs[peerId].RequestVote(currentTerm.get(), id, lastLogIndex, lastLogTerm);
        } catch (RemoteObjectException e) {
            logger.log(Level.SEVERE,"Failed to request vote from peer " + peerId + ": ",e);
        }
        return null;
    }

    /**
     * Generates a random election timeout duration.
     * <p>
     * This method returns a random election timeout within a predefined range. This randomness helps to ensure that split votes are
     * less likely by staggering the time nodes become candidates and start elections.
     * </p>
     *
     * @return a random election timeout duration in milliseconds.
     */
    private int generateRandomElectionTimeout() {
        return 500 + random.nextInt(100, 300);
    }
}


