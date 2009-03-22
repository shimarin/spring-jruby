package net.stbbs.spring.jruby.blazeds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyException;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import flex.messaging.MessageException;
import flex.messaging.config.ConfigMap;
import flex.messaging.messages.Message;
import flex.messaging.messages.RemotingMessage;
import flex.messaging.services.ServiceAdapter;
import flex.messaging.services.remoting.RemotingDestination;
import flex.messaging.util.ExceptionUtil;
import flex.messaging.util.MethodMatcher;
import flex.messaging.util.StringUtils;

public class JRubyAdapter extends ServiceAdapter {

	private Boolean printError = true;

	static Log logger = LogFactory.getLog(JRubyAdapter.class);	

	@Override
    public void initialize(String id, ConfigMap properties)
    {
		super.initialize(id, properties);
    }
/*
	protected Object passThroughToJava(Object bean,
			Message message,
			RemotingDestination remotingDestination,
			String beanName, String methodName, List parameters)
	{
		Object result = null;
        try
        {
            // Lookup and invoke.
            Class c = bean.getClass();

            MethodMatcher methodMatcher = remotingDestination.getMethodMatcher();
            Method method = methodMatcher.getMethod(c, methodName, parameters);
            result = method.invoke(bean, parameters.toArray());
        }
        catch (InvocationTargetException ex)
        {
            Throwable cause = ex.getCause();
            if ((cause != null) && (cause instanceof MessageException)) {
                throw (MessageException) cause;
            }
            else if (cause != null) {
                // Log a warning for this client's selector and continue
                if (logger.isErrorEnabled()) {
                    logger.error("Error processing remote invocation: " +
                         cause.toString() + StringUtils.NEWLINE +
                         "  incomingMessage: " + message + StringUtils.NEWLINE +
                         ExceptionUtil.toString(cause));
                }
                MessageException me = new MessageException(cause.getClass().getName() + " : " + cause.getMessage());
                me.setCode("Server.Processing");
                me.setRootCause(cause);
                throw me;
            }
            else
            {
                MessageException me = new MessageException(ex.getMessage());
                me.setCode("Server.Processing");
                throw me;
            }
        }
        catch (IllegalAccessException ex)
        {
            MessageException me = new MessageException(ex.getMessage());
            me.setCode("Server.Processing");
            throw me;
        }

        return result;
	}
*/
	@Override
	public Object invoke(Message message) {
		RemotingMessage rm = (RemotingMessage)message;
		RemotingDestination rd = (RemotingDestination)this.getDestination();
		String destination = message.getDestination();
		IRubyObject applicationContext = (IRubyObject)rd.getExtraProperty("applicationContext");
		String operation = rm.getOperation();
/*		RubyProc operationProc = null;
		synchronized (rd) {
			Map<String,RubyProc> operations = (Map<String,RubyProc>)rd.getExtraProperty("operations");
			if (operations != null) {
				operationProc = operations.get(operation);
			}
		}
*/		
		List params = rm.getParameters();
/*		if (operationProc == null) {
			if (bean != null) {
				return passThroughToJava(bean, message, rd, destination, operation, params);
			} else {
				// no such operation error
                MessageException me = new MessageException("Operation '" + operation + "' not found.");
        		me.setCode("Server.Processing");
                throw me;
			}
		}
*/		
		Ruby runtime = applicationContext.getRuntime();
		IRubyObject ro = applicationContext.callMethod(runtime.getCurrentContext(), "allocateProxyObject");
		IRubyObject[] args = new IRubyObject[params.size()];
		int i = 0;
		for (Object param:params) {
			args[i++] = Util.convertJavaToRuby(runtime, param);
		}
		ThreadContext context = runtime.getCurrentContext();
		
		try {
			IRubyObject rbean = ro.callMethod(context, destination);
			Object jo = JavaEmbedUtils.rubyToJava(runtime, rbean, null);
			if (!(jo instanceof IRubyObject)) {
	            Class c = (Class)jo.getClass();
	            MethodMatcher methodMatcher = rd.getMethodMatcher();
	            Method method = methodMatcher.getMethod(c, operation, params);
	            return method.invoke(jo, params.toArray());
			}
			//else 
			IRubyObject result = rbean.callMethod(context, operation, args);
			return net.stbbs.jruby.Util.convertRubyToJava(result);	// Ruby->Javaのディープコンバートをする
		}
		catch (RaiseException ex) {
			RubyException rex = ((RaiseException)ex).getException();
			String errorMessage = rex.message.toString();
			Throwable cause = ex.getCause();
			if (cause == null) {
				MessageException me = new MessageException(errorMessage);
				me.setCode("RaiseException");
				throw me;
			}
			//else
			if (cause instanceof MessageException) {
				throw (MessageException)cause;
			}
			// else
			MessageException me = new MessageException(cause.getMessage());
			me.setCode(cause.getClass().getName());
			me.setRootCause(cause);
			throw me;	
		}
        catch (InvocationTargetException ex)
        {
            Throwable cause = ex.getCause();
            if ((cause != null) && (cause instanceof MessageException)) {
                throw (MessageException) cause;
            }
            else if (cause != null) {
                // Log a warning for this client's selector and continue
                if (logger.isErrorEnabled()) {
                    logger.error("Error processing remote invocation: " +
                         cause.toString() + StringUtils.NEWLINE +
                         "  incomingMessage: " + message + StringUtils.NEWLINE +
                         ExceptionUtil.toString(cause));
                }
                MessageException me = new MessageException(cause.getClass().getName() + " : " + cause.getMessage());
                me.setCode("Server.Processing");
                me.setRootCause(cause);
                throw me;
            }
            else
            {
                MessageException me = new MessageException(ex.getMessage());
                me.setCode("Server.Processing");
                throw me;
            }
        }
        catch (IllegalAccessException ex)
        {
            MessageException me = new MessageException(ex.getMessage());
            me.setCode("Server.Processing");
            throw me;
        }
	}

}
