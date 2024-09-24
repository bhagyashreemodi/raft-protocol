package remote;

import java.lang.reflect.Proxy;

import static remote.ValidationUtil.isEmpty;
import static remote.ValidationUtil.isRemoteInterface;

/** Remote Object stub factory.
    <p>
    Remote Object stubs hide network communication with the remote server and 
    provide a simple object-like interface to their users. This class provides 
    methods for creating stub objects dynamically, when given pre-defined interfaces.
    <p>
    The network address of the remote Service is set when a stub is created, and
    may not be modified afterwards.
 */
public abstract class StubFactory {

    /** The first static <code>create</code> method to create a Stub accepts
        the desired remote interface and Service address.  This method should
        only be used when no loss or delay is desired for the network Sockets.
        <p>
        This method assumes the remote Service is already running at the
        specified address.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param addr   The network address of the Service as "ip:port"
        @return The stub created.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw <code>RemoteObjectException</code>.
        @throws NullPointerException If <code>c</code> is <code>null</code>.
     */
    public static <T> T create(Class<T> c, String addr) {
        return create(c, addr, false, false);
    }

    /** The second static <code>create</code> method to create a Stub accepts
        the desired remote interface and Service address, in addition to two
        boolean flags to configure loss and delay of the underlying network
        Sockets.
        <p>
        This method assumes the remote Service is already running at the
        specified address.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param addr   The network address of the Service as "ip:port"
        @param sockLoses  A flag that indicates whether or not Objects can be lost
                      between sender and receiver, resulting in timeout.
        @param sockDelays A flag that indicates whether propagation delay is incurred
                      when sending an Object from sender to receiver.
        @return The stub created.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw <code>RemoteObjectException</code>.
        @throws NullPointerException If <code>c</code> is <code>null</code>.
     */    
    public static <T> T create(Class<T> c, String addr, boolean sockLoses, boolean sockDelays) {
        if(isEmpty(c))
            throw new NullPointerException("Class is null");
        if(isEmpty(addr))
            throw new NullPointerException("Address is null/blank");
        if(!isRemoteInterface(c))
            throw new Error("Class does not represent a remote interface");
        T stub = (T) Proxy
                .newProxyInstance(
                        c.getClassLoader(),
                        new Class<?>[] { c },
                        new StubInvocationHandler(c, addr, sockLoses, sockDelays));
        return stub;
    }
}
