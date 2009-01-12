package net.stbbs.spring.jruby.blazeds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyProc;
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

public class SpringJRubyAdapter extends ServiceAdapter {

	private Boolean printError = true;

	static Log logger = LogFactory.getLog(SpringJRubyAdapter.class);	

	@Override
    public void initialize(String id, ConfigMap properties)
    {
		super.initialize(id, properties);
    }
	
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
	
	@Override
	public Object invoke(Message message) {
		RemotingMessage rm = (RemotingMessage)message;
		RemotingDestination rd = (RemotingDestination)this.getDestination();
		String destination = message.getDestination();
		Object bean = rd.getExtraProperty("bean");
		String operation = rm.getOperation();
		RubyProc operationProc = null;
		synchronized (rd) {
			Map<String,RubyProc> operations = (Map<String,RubyProc>)rd.getExtraProperty("operations");
			if (operations != null) {
				operationProc = operations.get(operation);
			}
		}
		
		List params = rm.getParameters();
		if (operationProc == null) {
			if (bean != null) {
				return passThroughToJava(bean, message, rd, destination, operation, params);
			} else {
				// no such operation error
                MessageException me = new MessageException("Operation '" + operation + "' not found.");
        		me.setCode("Server.Processing");
                throw me;
			}
		}
		
		Ruby runtime = operationProc.getRuntime();
		IRubyObject ro = runtime.getClass("ApplicationContext").allocate();
		IRubyObject[] args = new IRubyObject[params.size()];
		int i = 0;
		for (Object param:params) {
			args[i++] = Util.convertJavaToRuby(runtime, param);
		}
		IRubyObject result = operationProc.call(args, ro, null);
		return net.stbbs.jruby.Util.convertRubyToJava(result);
	}

}
