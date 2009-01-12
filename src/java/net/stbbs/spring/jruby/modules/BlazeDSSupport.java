package net.stbbs.spring.jruby.modules;

import java.util.HashMap;
import java.util.Map;

import org.jruby.RubyProc;

import flex.messaging.Destination;
import flex.messaging.MessageBroker;
import flex.messaging.services.Service;

public class BlazeDSSupport {
    public static final String DEFAULT_MESSAGEBROKER_ID = "jrubyamf";
    public static final String DEFAULT_MESSAGE_SERVICE_ID = "messaging-service";
    public static final String DEFAULT_REMOTING_SERVICE_ID = "remoting-service";

	protected static Destination getRemotingDestination(String destinationId)
	{
		return getRemotingService().getDestination(destinationId);
	}
	
	protected static Service getRemotingService()
	{
		return MessageBroker.getMessageBroker(DEFAULT_MESSAGEBROKER_ID).getService(DEFAULT_REMOTING_SERVICE_ID);
	}
	
	public static Destination createDestination(String destinationId)
	{
		Destination dest = getRemotingService().createDestination(destinationId);
	    dest.setChannels(getRemotingService().getDefaultChannels());
	    return dest;
	}
	
	public void registerDestination(String destinationId, Object bean)
	{
		Destination dest = getRemotingDestination(destinationId);
		if (dest == null) {
			dest = createDestination(destinationId);
		}
		synchronized(dest) {
			dest.addExtraProperty("bean", bean);
		}
	}
	
	public void removeDestination(String destinationId)
	{
		getRemotingService().removeDestination(destinationId);
	}
	
	public void registerOperation(String destinationId, String operationName, RubyProc operation)
	{
		Destination dest = getRemotingDestination(destinationId);
		if (dest == null) {
			dest = createDestination(destinationId);
		}
		synchronized(dest) {
			Map<String,RubyProc> operations = (Map<String,RubyProc>)dest.getExtraProperty("operations");
			if (operations == null) {
				operations = new HashMap<String,RubyProc>();
				dest.addExtraProperty("operations", operations);
			}
			operations.put(operationName, operation);
		}
	}
	
	public boolean removeOperation(String destinationId, String operationName)
	{
		Destination dest = getRemotingDestination(destinationId);
		if (dest == null) return false;
		synchronized(dest) {
			Map<String,RubyProc> operations = (Map<String,RubyProc>)dest.getExtraProperty("operations");
			if (operations == null) return false;
			operations.remove(operationName);
		}
		return true;
	}

}
