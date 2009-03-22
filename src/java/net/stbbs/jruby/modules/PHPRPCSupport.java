package net.stbbs.jruby.modules;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.phprpc.PHPRPC_Client;
import org.phprpc.PHPRPC_Error;
import org.phprpc.util.AssocArray;

public class PHPRPCSupport {
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, PHPRPC_ClientDecorator.class);
	}
	
	@JRubyMethod(required=1, optional=2)
	public PHPRPC_Client newPHPRPCClient(IRubyObject self, IRubyObject[] args, Block block)
	{
		String host = args[0].asString().getUnicodeValue();
		PHPRPC_Client client = new PHPRPC_Client();
		boolean success;
		if (args.length > 2) {
			String username = args[1].asString().getUnicodeValue();
			String password = args[2].asString().getUnicodeValue();
			success = client.useService(host, username, password);
		} else {
			success = client.useService(host);
		}
		return success? client : null;
	}
	
	@Decorator(PHPRPC_Client.class)
	public static class PHPRPC_ClientDecorator {
		private PHPRPC_Client client;
		
		public PHPRPC_ClientDecorator(PHPRPC_Client client)
		{
			this.client = client;
		}
		
		@JRubyMethod(required=1)
		public Object method_missing(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException, PHPRPC_Error
		{
			Ruby runtime = self.getRuntime();
			String methodName = args[0].asString().getUnicodeValue();
			Object[] remoteArgs = new Object[args.length - 1];
			for (int i = 1; i < args.length; i++) {
				remoteArgs[i - 1] = ruby2php(runtime, args[i]);
			}
			Object result = client.invoke(methodName, remoteArgs);
			if (client.getWarning() != null) {
				throw (PHPRPC_Error)client.getWarning();
			}
			if (result instanceof PHPRPC_Error) {
				throw (PHPRPC_Error)result;
			}
			return php2java(runtime, result);
		}
		
		@JRubyMethod(required=1)
		public Object call(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException, PHPRPC_Error
		{
			return method_missing(self, args, block);
		}
		
		private Object ruby2php(Ruby runtime, IRubyObject ro) {
			if (ro instanceof RubyHash) {
				Map map = new HashMap();
				RubyHash hash = (RubyHash)ro;
				for (Object e:hash.entrySet()) {
					Map.Entry entry = (Map.Entry)e;
					Object key = entry.getKey();
					Object value = entry.getValue();
					if (key instanceof IRubyObject) key = ruby2php(runtime, (IRubyObject)key);
					if (value instanceof IRubyObject) value = ruby2php(runtime, (IRubyObject)value);
					map.put(entry.getKey(), entry.getValue());
				}
				return map;
			}
			return JavaEmbedUtils.rubyToJava(runtime, ro, null);	
		}
		
		private Object php2java(Ruby runtime, Object result) throws UnsupportedEncodingException
		{
			if (result instanceof byte[]) {
				result = new String((byte[])result, "UTF-8");
			} else if (result instanceof AssocArray) {
				result = php2java(runtime, ((AssocArray)result).toHashMap());
			} else if (result instanceof Map) {
				Map map = (Map)result;
				if (map.containsKey("m_milliseconds") && map.containsKey("m_timeZone")) {	// ORBDatetimeとみなす
					result = new java.util.Date((Long)map.get("m_milliseconds"));
				} else {
					RubyHash hash = RubyHash.newHash(runtime);
					for (Object e:map.entrySet()) {
						Map.Entry entry = (Map.Entry)e;
						// 循環参照についてはとりあえず考慮しない
						Object key = php2java(runtime, entry.getKey());
						IRubyObject rkey = key instanceof String? runtime.newSymbol((String)key) : JavaEmbedUtils.javaToRuby(runtime, key);
						hash.op_aset(rkey, JavaEmbedUtils.javaToRuby(runtime, php2java(runtime, entry.getValue())) );
					}
					result = hash;
				}
			}
			return result;
		}
	}
}
