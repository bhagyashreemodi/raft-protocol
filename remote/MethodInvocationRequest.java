package remote;

import java.io.Serializable;

/**
 * Represents a request for invoking a method on a remote object.
 * This class is used to serialize method invocation details over the network
 * from a client stub to the remote service. It includes the name of the method
 * to be called, the parameter types, and the arguments to pass to the method.
 */
public class MethodInvocationRequest implements Serializable {
    private static final long serialVersionUID = 1L; // UID for serialization

    private String methodName; // The name of the method to be invoked
    private Class<?>[] paramTypes; // The types of the parameters for the method
    private Object[] args; // The arguments to be passed to the method

    /**
     * Constructs a new method invocation request.
     *
     * @param methodName The name of the method to invoke.
     * @param paramTypes The types of the parameters that the method accepts.
     * @param args The arguments to pass to the method.
     */
    public MethodInvocationRequest(String methodName, Class<?>[] paramTypes, Object[] args) {
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.args = args;
    }

    /**
     * Gets the name of the method to be invoked.
     *
     * @return The method name.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the types of the parameters for the method.
     *
     * @return An array of {@link Class} objects representing the parameter types.
     */
    public Class<?>[] getParamTypes() {
        return paramTypes;
    }

    /**
     * Gets the arguments to be passed to the method.
     *
     * @return An array of {@link Object} containing the arguments for the method.
     */
    public Object[] getArgs() {
        return args;
    }
}
