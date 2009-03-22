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
    public static final String DESTINATION_CLASSVAR_NAME = "@@blazeds_destinations";
    public static final String REMOTING_ENDPOINT_URL_CLASSVER_NAME = "@@remoting_endpoint_url";
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
/*	
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
*/	
	@JRubyMethod(required=1)
	static public void included(IRubyObject self, IRubyObject[] args, Block block)
	{
		final RubyModule targetModule = (RubyClass)args[0];
		targetModule.defineModuleFunction("destination", new Callback() {

			public IRubyObject execute(final IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				RubyString destName = args[0].asString();
				RubyHash dests;
				if (targetModule.isClassVarDefined(DESTINATION_CLASSVAR_NAME)) {
					dests = (RubyHash)targetModule.getClassVar(DESTINATION_CLASSVAR_NAME);
				} else {
					dests = RubyHash.newHash(runtime);
					targetModule.setClassVar(DESTINATION_CLASSVAR_NAME, dests);
				}
				dests.put(args[0], args.length > 1? args[1] : RubyHash.newHash(runtime));
				return runtime.getNil();
			}

			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
			
		});
		targetModule.defineModuleFunction("remoting_endpoint_url", new Callback() {
			public IRubyObject execute(final IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				targetModule.setClassVar(REMOTING_ENDPOINT_URL_CLASSVER_NAME, args[0].asString());
				return runtime.getNil();
			}

			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
		});
	}


}
