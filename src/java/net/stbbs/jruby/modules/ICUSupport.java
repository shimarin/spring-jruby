package net.stbbs.jruby.modules;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

import com.ibm.icu.text.Normalizer;

public class ICUSupport {
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, StringDecorator.class);
	}
	
	@Decorator(String.class)
	public static class StringDecorator {
		@JRubyMethod
		public String utf8nfkc(IRubyObject self, IRubyObject[] args, Block block) {
			String str = self.asString().getUnicodeValue(); 
			return Normalizer.normalize(str, Normalizer.NFKC);
		}
	}

}
