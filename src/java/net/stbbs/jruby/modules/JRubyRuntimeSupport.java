package net.stbbs.jruby.modules;

import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class JRubyRuntimeSupport {
	
	@JRubyMethod
	static public Ruby runtime(IRubyObject self, IRubyObject[] args, Block block)
	{
		return self.getRuntime();
	}
}
