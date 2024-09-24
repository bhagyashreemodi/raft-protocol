package remote;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * A proxy invocation handler that facilitates remote method invocation.
 * It handles the dynamics of establishing a connection, sending the method invocation request,
 * receiving the response, and appropriately handling exceptions or errors during the process.
 * This class is designed to work with a custom implementation of sockets that may simulate
 * network characteristics like delays or losses.
 */
public class StubInvocationHandler implements InvocationHandler {
    private final Class<?> remoteInterface;
    private final String address;
    private final boolean sockLoses;
    private final boolean sockDelays;

    /**
     * Constructs an instance of StubInvocationHandler.
     * 
     * @param c The remote interface Class object that the proxy implements.
     * @param addr The address of the remote server to connect to.
     * @param sockLoses A flag indicating whether the socket simulates packet loss.
     * @param sockDelays A flag indicating whether the socket simulates network delays.
     */
    public StubInvocationHandler(Class<?> c, String addr, boolean sockLoses, boolean sockDelays) {
        this.remoteInterface = c;
        this.address = addr;
        this.sockLoses = sockLoses;
        this.sockDelays = sockDelays;
    }

    /**
     * Handles the dynamic invocation of methods on the proxy instance.
     * 
     * @param proxy The proxy instance that the method was invoked on.
     * @param method The Method instance corresponding to the interface method invoked on the proxy instance.
     * @param args An array of objects containing the values of the arguments passed in the method invocation on the proxy instance.
     * @return The result of the method invocation.
     * @throws Throwable If any exception occurs during the method invocation process.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final int MAX_ATTEMPTS = 5; // Maximum number of attempts
        LeakySocket socket = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                socket = new LeakySocket(address, sockLoses, sockDelays);
                System.out.println("Attempt " + attempt + ": Invoking method " + method.getName());
                MethodInvocationRequest request = new MethodInvocationRequest(method.getName(), method.getParameterTypes(), args);

                boolean success = socket.sendObject(request);
                if (!success) {
                    System.out.println("Send failed, attempting retry...");
                    continue; // If send fails, attempt retry
                }

                Object response = socket.recvObject();
                if (!(response instanceof MethodInvocationResponse)) {
                    throw new IOException("Invalid response type received.");
                }
                MethodInvocationResponse methodResponse = (MethodInvocationResponse) response;
                if (methodResponse.getException() != null) {
                    Throwable exception = methodResponse.getException();
                    System.out.println("[StubInvocationHandler] Received exception: " + exception.getClass().getName() + " - " + exception.getMessage());
                    if (exception instanceof NoSuchMethodException) {
                        throw new RemoteObjectException("Method not found: " + method.getName(), exception);
                    } else {
                        throw exception;
                    }
                } else {
                    return methodResponse.getResult();
                }
            } catch (IOException e) {
                System.out.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    System.out.println("Final attempt failed due to IOException, indicating possible connection issues.");
                    throw e;
                }
            } finally {
                if (socket != null) {
                    socket.close(); 
                }
            }
        }
        throw new RemoteObjectException("Unable to establish connection after " + MAX_ATTEMPTS + " attempts.");
    }
}
