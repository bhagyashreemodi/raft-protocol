package remote;

import java.lang.reflect.Method;

public class ValidationUtil {

    /**
     * Check if the provided interface is a remote interface.
     *
     * @param c The interface class to check.
     * @return True if the interface is considered remote, false otherwise.
     */
    public static boolean isRemoteInterface(Class c) {
        for (Method method : c.getMethods()) {
            if (!throwsRemoteObjectException(method)) {
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
    private static boolean throwsRemoteObjectException(Method method) {
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            if (RemoteObjectException.class.isAssignableFrom(exceptionType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(Class s) {
        return s == null;
    }

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty() || s.isBlank();
    }
}
