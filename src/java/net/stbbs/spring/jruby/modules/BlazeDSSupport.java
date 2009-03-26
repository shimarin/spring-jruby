package net.stbbs.spring.jruby.modules;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

import flex.messaging.Destination;
import flex.messaging.MessageBroker;
import flex.messaging.services.Service;

public class BlazeDSSupport {
    public static final String DEFAULT_MESSAGEBROKER_ID = "jrubyamf";
    public static final String DEFAULT_MESSAGE_SERVICE_ID = "messaging-service";
    public static final String DEFAULT_REMOTING_SERVICE_ID = "remoting-service";
    public static final String ENDPOINT_URL_BASE = "http://{server.name}:{server.port}/{context.root}";
    public static final String DEFAULT_REMOTING_ENDPOINT_URL = "/rubymessagebroker/amf";

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
}
