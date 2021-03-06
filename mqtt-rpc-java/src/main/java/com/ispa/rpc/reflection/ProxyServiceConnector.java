/*******************************************************************************
 *******************************************************************************/
package com.ispa.rpc.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.Function;

import com.ispa.rpc.generic.ServiceConnector;
import com.ispa.rpc.generic.SilentCloseable;

/**
 * Implementation of {@link com.ispa.rpc.generic.ServiceConnector}, delegating transport calls to a given {@link com.ispa.rpc.generic.ServiceHost}.
 *
 * @param <T> remote service type
 * @author Philipp Gayret
 */
public abstract class ProxyServiceConnector<T> implements ServiceConnector<T> {

    private static final Object NO_RESULT = new Object();

    private final Class<T> type;

    public ProxyServiceConnector(Class<T> type) {
        this.type = type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> SilentCloseable drpc(Function<T, R> invoker, Consumer<R> consumer) {
        Invocation invocation = Invocation.one(type, invoker::apply);
        return this.call(type, invocation.getMethod(), null, invocation.getArguments(), consumer);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("all")
    @Override
    public T connect(String identifier) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, arguments) -> {
            Object[] results = new Object[]{NO_RESULT};
            SilentCloseable listener = this.call(type, method, identifier, arguments, (result) -> {
                synchronized (results) {
                    results[0] = result;
                    results.notifyAll();
                }
            });
            synchronized (results) {
                while (results[0] == NO_RESULT) {
                    results.wait();
                }
                listener.close();
            }
            return results[0];
        });
    }

    /**
     * Should perform a remote procedure call, any responses must be forwarded to the consumer.
     *
     * @param type       service connector type
     * @param method     invoked method
     * @param identifier service identifier, if calling a service with an idetifier. leave null otherwise
     * @param arguments  invocation arguments
     * @param consumer   response handler
     * @param <R>        response type
     * @return {@link SilentCloseable} used to remove the consumer as a response handler.
     */
    public abstract <R> SilentCloseable call(Class<?> type, Method method, String identifier, Object[] arguments, Consumer<R> consumer);

}
