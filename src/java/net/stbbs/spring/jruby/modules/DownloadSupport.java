package net.stbbs.spring.jruby.modules;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class DownloadSupport {
	
	static public void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, ByteArrayDecorator.class);
	}
	
	@Decorator(byte[].class)
	static public class ByteArrayDecorator {
		private byte[] self;
		public ByteArrayDecorator(byte[] self)
		{
			this.self = self;
		}
		@JRubyMethod
		public DownloadContent download(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (args.length == 0) {
				return new DownloadContent(this.self);
			}
			// else
			return new DownloadContent(args[0].asString().getUnicodeValue(), this.self);
		}
	}
	
}
