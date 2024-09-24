package remote;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;


/** Remote Object Service
 * @param <T> The interface that the Service will handle method call requests for.
    <p>
    A <code>Service</code> encapsulates a multithreaded TCP server and allows connections
    from client Stubs that are created using the <code>StubFactory</code>.
    <p>
    The <code>Service</code> class is parametrized by a type variable. This type variable
    should be instantiated with an interface. The <code>Service</code> will accept calls
    from <code>Stubs</code> to invoke methods on this interface.  It will then forward those
    requests to an instantiated <code>Object</code>, which is specified when the <code>Service</code>
    is constructed.  The <code>Object</code> must implement the remote interface.  Each method
    in the interface should be marked as throwing <code>RemoteObjectException</code>,
    in addition to any other exceptions as needed.
    <p>
    Exceptions may occur at the top level in the listening and service threads.
    The <code>Service</code>'s response to these exceptions can be customized by deriving
    a class from <code>Service</code> and overriding <code>listen_error</code>
    or <code>service_error</code>.
*/
public class Service<T> {

    /** The class of the interface that defines the methods available for remote invocation. */
    private final Class<T> interfaceClass;

    /** The instantiated service object on which the remote methods will be invoked. */
    private final T serviceObject;

    /** The port number on which the server will listen for incoming connection requests. */
    private final int port;

    /** Flag indicating whether simulated network loss should occur. */
    private final boolean lossy;

    /** Flag indicating whether simulated network delay should occur. */
    private final boolean delayed;

    /** AtomicBoolean flag to safely check and manage the service's running state across threads. */
    private AtomicBoolean isServiceRunning = new AtomicBoolean(false);;

    /** The server socket that listens for incoming connections. */
    private ServerSocket serverSocket;

    /** The thread that listens for incoming connection requests. */
    private Thread listenThread;

    private AtomicInteger count = new AtomicInteger(0);

    /** The first constructor creates a <code>Service</code> that is bound to
        a given remote interface, instantiated object, and server port number.
        This constructor is used when no loss or delay is desired for the
        network Sockets.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param svc    An instantiated object that implements the interface
                      indicated by <code>c</code>.  Upon receipt of requests for
                      method calls, the Service invokes those calls on this object.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw 
                      <code>RemoteObjectException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>svc</code> is <code>null</code>.
     */
    public Service(Class<T> c, T svc, int port) {
        this(c, svc, port, false, false);
    }

    /** The second constructor creates a <code>Service</code> similar to the 
        first one, but with additional parameters to enable simulated loss
        and/or delay of Objects being sent over network Sockets.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param svc    An instantiated object that implements the interface
                      indicated by <code>c</code>.  Upon receipt of requests for
                      method calls, the Service invokes those calls on this object.
        @param lossy  A flag that indicates whether or not Objects can be lost
                      between sender and receiver, resulting in timeout.
        @param delayed A flag that indicates whether propagation delay is incurred
                      when sending an Object from sender to receiver.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw 
                      <code>RemoteObjectException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>svc</code> is <code>null</code>.
     */
    public Service(Class<T> c, T svc, int port, boolean lossy, boolean delayed) {
        if (c == null || svc == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }

        if (!isRemoteInterface(c)) {
            throw new Error("Class does not represent a remote interface.");
        }

        this.interfaceClass = c;
        this.serviceObject = svc;
        this.port = port;
        this.lossy = lossy;
        this.delayed = delayed;
    }

    public boolean isRunning() {
        return isServiceRunning.get();
    }

    /**
     * Check if the provided interface is a remote interface.
     *
     * @param c The interface class to check.
     * @return True if the interface is considered remote, false otherwise.
     */
    private boolean isRemoteInterface(Class<T> c) {
        // Check if all methods declare RemoteObjectException
        for (Method method : c.getMethods()) {
            if (!declaresRemoteObjectException(method)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a method declares RemoteObjectException.
     *
     * @param method The method to check.
     * @return True if the method declares RemoteObjectException, false otherwise.
     */
    private boolean declaresRemoteObjectException(Method method) {
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            if (RemoteObjectException.class.isAssignableFrom(exceptionType)) {
                return true;
            }
        }
        return false;
    }
    
    /** When the listening thread exits, it should call <code>stopped</code>.
        <p>
        The parameter passed from the listening thread allows the <code>Service</code>
        to react accordingly, namely whether the thread stops due to an exception
        or a call to <code>stop</code>.
        <p>
        When this method is called, the calling thread owns the lock on the
        <code>Service</code> object. Care must be taken to avoid deadlocks when
        calling <code>start</code> or <code>stop</code> from different threads
        during this call.
        <p>
        The default implementation does nothing.
        @param cause The exception that stopped the Service, or
                     <code>null</code> if the Service stopped normally.
     */  
    protected void stopped(Throwable cause) {
    }

    /** When an exception occurs in the listening thread, it also calls
        <code>listen_error</code>.
        <p>
        The intent of this method is to allow the user to report exceptions in
        the listening thread to another thread, by a mechanism of the user's
        choosing. The user may also ignore the exceptions. The default
        implementation simply stops the server. The user should not use this
        method to stop the Service. The exception will again be provided as the
        argument to <code>stopped</code>, which will be called later.
        @param exception The exception that occurred.
        @return <code>true</code> if the service should resume accepting
                connections, <code>false</code> if the service should shut down.
     */
    protected boolean listen_error(Exception exception) {
        return false;
    }

    /** When an exception occurs in a service thread, <code>service_error</code>
        is called, similar to previous.
        <p>
        The default implementation does nothing.
        @param exception The exception that occurred.
     */
    protected void service_error(RemoteObjectException exception) {
    }

    /** The Service is started using <code>start</code>.
        <p>
        A thread is created to listen for connection requests on the port
        specified when the Service was constructed.  The network address
        can be learned using suitable Socket APIs.  After creating the listening
        thread, this method should return immediately.
        <p>
        The <code>synchronized</code> keyword may need to be added, depending
        on the implementation.
        @throws RemoteObjectException When the listening socket cannot be created or
                bound, when the listening thread cannot be created, or when the server
                has already been started and has not since stopped.
     */
    public void start() throws RemoteObjectException {
        if (listenThread != null && listenThread.isAlive()) {
            throw new RemoteObjectException("Service is already running.");
        }

        try {
            serverSocket = new ServerSocket(port);
            isServiceRunning.set(true);
            listenThread = new Thread(this::listenForConnections);
            listenThread.start();
        } catch (IOException e) {
            if (!listen_error(e)) {
                throw new RemoteObjectException("Error starting the service.", e);
            }
        }
    }

    /**
     * Listens for connection requests and handles them by creating new service threads.
     * <p>
     * This method continuously listens for incoming connection requests as long as
     * the service is running and the server socket has not been closed. For each
     * connection request, it initializes a {@link LeakySocket} and starts a new
     * {@link ServiceThread} to handle method invocation requests from the client.
     * <p>
     * Note: This method is intended to be run in a separate thread and will exit
     * when the service is stopped or when an unrecoverable error occurs.
     */
    private void listenForConnections() {
        while (isServiceRunning.get() && !serverSocket.isClosed())  {
            try {
                Socket clientSocket = serverSocket.accept();

                // Use LeakySocket for communication
                LeakySocket leakySocket = new LeakySocket(clientSocket, lossy, delayed);

                // Handle the incoming connection and create service threads
                ServiceThread serviceThread = new ServiceThread(interfaceClass, serviceObject, leakySocket);
                serviceThread.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    listen_error(e);
                }
                break;
            }
        }
    }

    /**
     * Constructs a new service thread to handle individual client connections.
     */
    private class ServiceThread extends Thread {
        private final Class<T> interfaceClass;
        private final T serviceObject;
        private final LeakySocket leakySocket;

        /**
         * Constructs a new service thread.
         *
         * @param interfaceClass The class of the interface that defines the remote methods.
         * @param serviceObject An object that implements the given interface, to which method calls will be delegated.
         * @param leakySocket The socket through which communication with the client will occur, possibly simulating loss and delay.
         */
        public ServiceThread(Class<T> interfaceClass, T serviceObject, LeakySocket leakySocket) {
            this.interfaceClass = interfaceClass;
            this.serviceObject = serviceObject;
            this.leakySocket = leakySocket;
        }

        /**
         * The main execution method for the service thread.
         * Waits for method invocation requests from the client, invokes the requested method on the service object,
         * and sends back the result or any exceptions that occurred.
         */
        @Override
        public void run() {
            try {
                if (!isServiceRunning.get()) {
                    System.out.println("Service has stopped. No longer accepting requests.");
                    return;
                }

                Object requestObject = leakySocket.recvObject();
                if (!(requestObject instanceof MethodInvocationRequest)) {
                    // Handle invalid request
                    return;
                }

                MethodInvocationRequest request = (MethodInvocationRequest) requestObject;
                Method method;

                try {
                    method = serviceObject.getClass().getMethod(request.getMethodName(), request.getParamTypes());
                } catch (NoSuchMethodException nsme) {
                    System.out.println("[ServiceThread] Method " + request.getMethodName() + " not found, sending exception back to client.");
                    MethodInvocationResponse response = new MethodInvocationResponse(null, new RemoteObjectException("Method " + request.getMethodName() + " not found."));
                    leakySocket.sendObject(response);
                    return;
                }

                if (!isServiceRunning.get()) {
                    throw new IllegalStateException("Service has been stopped.");
                }

                Object result = null;
                Throwable exception = null;
                count.incrementAndGet();
                synchronized (serviceObject) {
                    try {
                        result = method.invoke(serviceObject, request.getArgs());
                    } catch (InvocationTargetException e) {
                        exception = e.getTargetException();
                    } catch (Exception e) {
                        exception = e;
                    }
                }

                MethodInvocationResponse response = new MethodInvocationResponse(result, exception);
                leakySocket.sendObject(response);

            } catch (Exception e) {
                try {
                    System.out.println("[ServiceThread] Error during method invocation: " + e.getMessage());
                    Throwable actualException = e instanceof InvocationTargetException ? e.getCause() : e;
                    MethodInvocationResponse response = new MethodInvocationResponse(null, actualException);
                    leakySocket.sendObject(response);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } finally {
                leakySocket.close();
            }
        }
    }



    /** The Service is stopped using <code>stop</code>, if it is running.
        <p>
        This terminates the listening thread and calls other methods as
        needed.
        <p>
        The <code>synchronized</code> keyword may be needed, depending
        on the implementation.
     */
    public void stop() {
        isServiceRunning.set(false);

        try {
            // close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e.getMessage());
            e.printStackTrace();
        }

        // stop listening thread
        if (listenThread != null && listenThread.isAlive()) {
            listenThread.interrupt();
            try {
                listenThread.join(1000); // wait for at most 1s
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for listen thread to finish: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        stopped(null);
    }

    public int getCount() {
        return count.get();
    }


}

