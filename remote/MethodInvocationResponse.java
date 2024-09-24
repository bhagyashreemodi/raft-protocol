package remote;

import java.io.Serializable;

/**
 * Represents the response of a method invocation.
 * This class encapsulates the result of a remote method call, including any return value
 * or exception that occurred during the method's execution.
 */
public class MethodInvocationResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private Object result; // The result of the method call, if successful
    private Throwable exception; // The exception thrown by the method call, if any

    /**
     * Constructs a new response object for a method invocation.
     *
     * @param result The result of the method invocation, or null if an exception occurred.
     * @param exception The exception thrown during the method invocation, or null if the call was successful.
     */
    public MethodInvocationResponse(Object result, Throwable exception) {
        this.result = result;
        this.exception = exception;
    }

    /**
     * Retrieves the result of the method invocation.
     *
     * @return The result object, or null if there was an exception.
     */
    public Object getResult() {
        return result;
    }

    /**
     * Retrieves the exception thrown during the method invocation, if any.
     *
     * @return The exception object, or null if the call was successful.
     */
    public Throwable getException() {
        return exception;
    }
}
