package remote;

/** A custom exception that is used to indicate that a method implementation
    involves invocation of a method on a remote object over a network connection. */
public class RemoteObjectException extends Exception {

    /** Creates a <code>RemoteObjectException</code> with the given message string. */
    public RemoteObjectException(String message) {
        super(message);
    }

    /** Creates a <code>RemoteObjectException</code> with a message string and the given cause. */
    public RemoteObjectException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Creates a <code>RemoteObjectException</code> from the given cause. */
    public RemoteObjectException(Throwable cause) {
        super(cause);
    }
}
