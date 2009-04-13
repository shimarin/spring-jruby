package net.stbbs.spring.jruby.modules;

import javax.naming.NamingException;

import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.jndi.JndiTemplate;

public class JNDISupport {
	@JRubyMethod
	public Object lookupJNDI(IRubyObject self, IRubyObject[] args, Block block) throws IllegalArgumentException, NamingException
	{
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError(args.length, 1);
		}
		JndiTemplate jt = new JndiTemplate();
		return jt.lookup(args[0].asString().getUnicodeValue());
	}
}
