package net.stbbs.jruby.modules;

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.SimpleDateFormat;

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
		
		@JRubyMethod
		public RubyTime parseRFC822Date(IRubyObject self, IRubyObject[] args, Block block) throws ParseException
		{
			String str = self.asString().getUnicodeValue();
			return RubyTime.newTime(
					self.getRuntime(), 
					new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(str).getTime()
			);
			
		}
	}

}
