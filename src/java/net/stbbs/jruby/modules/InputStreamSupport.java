package net.stbbs.jruby.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class InputStreamSupport {

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, InputStreamDecorator.class);
	}

	@Decorator(InputStream.class)
	static public class InputStreamDecorator {

		private InputStream is;
		private InputStreamReader insr = null;
		private BufferedReader br = null;
		
		public InputStreamDecorator(InputStream is)
		{
			this.is = is;
		}
		
		private InputStreamReader getInputStreamReader() throws UnsupportedEncodingException
		{
			if (insr == null) {
				insr = new InputStreamReader(is, "UTF-8");
			}
			return insr;
		}
		
		private BufferedReader getBufferedReader() throws UnsupportedEncodingException
		{
			if (br == null) {
				br = new BufferedReader(getInputStreamReader());
			}
			return br;
		}
		
		@JRubyMethod
		public String gets(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException, IOException
		{
			return getBufferedReader().readLine();
		}
		
		@JRubyMethod
		public IRubyObject each(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException, IOException
		{
			Ruby runtime = self.getRuntime();
			String line;
			while ((line = getBufferedReader().readLine()) != null) {
				if (block.isGiven()) {
					block.call(runtime.getCurrentContext(), 
						new IRubyObject[] {RubyString.newUnicodeString(runtime, line)});
				}
			}
			return self;
		}

		@JRubyMethod
		public IRubyObject each_line(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException, IOException
		{
			return each(self, args, block);
		}
		
		@JRubyMethod
		public String readAll(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			BufferedReader br = getBufferedReader();
			int c;
			StringBuffer buf = new StringBuffer();
			while ((c = br.read()) >= 0) {
				buf.append((char)c);
			}
			return buf.toString();
		}
	}
}
