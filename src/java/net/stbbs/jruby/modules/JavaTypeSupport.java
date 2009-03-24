package net.stbbs.jruby.modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyFile;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.javasupport.JavaObject;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

public class JavaTypeSupport {

	public static void onRegister(RubyModule module)
	{
		final Ruby runtime = module.getRuntime();
		runtime.getFixnum().defineMethod("to_java_int", new Callback() {

			public IRubyObject execute(IRubyObject self, IRubyObject[] args,Block block) {
				return JavaObject.wrap(runtime, new Integer(RubyFixnum.fix2int(self)));
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
			
		});
		Util.registerDecorator(runtime, StringDecorator.class);
		Util.registerDecorator(runtime, ByteArrayDecorator.class);
	}
	
	@Decorator(String.class)
	public static class StringDecorator {
		@JRubyMethod
		public byte[] toByteArray(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException {
			String str = self.asString().getUnicodeValue(); 
			return str.getBytes("UTF-8");
		}
		
		@JRubyMethod
		public String inspect(IRubyObject self, IRubyObject[] args,Block block) {
			String str = self.asString().getUnicodeValue(); 
			return '"' + str + '"';
		}
		
	}

	@Decorator(byte[].class)
	static public class ByteArrayDecorator {
		private byte[] self;
		public ByteArrayDecorator(byte[] self)
		{
			this.self = self;
		}
		
		@JRubyMethod 
		public String base64(IRubyObject self, IRubyObject[] args, Block block)
		{
			return encodeBase64(this.self);
		}
		
		@JRubyMethod
		public void save(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw runtime.newArgumentError(args.length, 1);
			}
			// else
			if (args[0] instanceof RubyFile) {
				RubyFile file = (RubyFile)args[0];
				OutputStream os = file.getOutStream();
				os.write(this.self);
				os.flush();
				return;
			}

			Object jo = JavaUtil.convertRubyToJava(args[0]);
			if (jo instanceof File) {
				 File file = (File)jo;
				 FileOutputStream fos = new FileOutputStream(file);
				 fos.write(this.self);
				 fos.flush();
				 fos.close();
			} else if (jo instanceof OutputStream) {
				OutputStream os = (OutputStream)jo;
				os.write(this.self);
				os.flush();
			} else {
				String filename = args[0].asString().getUnicodeValue();
				FileOutputStream fos = new FileOutputStream(filename);
				try {
					save(self, new IRubyObject[] { JavaEmbedUtils.javaToRuby(runtime, fos) }, block);
				}
				finally {
					fos.close();
				}
				
			}
		}
	}

	public static String encodeBase64(byte[] data)
	{
		 final char[] base64EncodeChars = new char[] {
			        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
			        'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
			        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
			        'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
			        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
			        'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
			        'w', 'x', 'y', 'z', '0', '1', '2', '3',
			        '4', '5', '6', '7', '8', '9', '+', '/' };

		 StringBuffer sb = new StringBuffer();
        int r = data.length % 3;
        int len = data.length - r;
        int i = 0;
        int c;
        while (i < len) {
            c = (0x000000ff & data[i++]) << 16 |
                (0x000000ff & data[i++]) << 8  |
                (0x000000ff & data[i++]);
            sb.append(base64EncodeChars[c >> 18]);
            sb.append(base64EncodeChars[c >> 12 & 0x3f]);
            sb.append(base64EncodeChars[c >> 6  & 0x3f]);
            sb.append(base64EncodeChars[c & 0x3f]);
        }
        if (r == 1) {
            c = 0x000000ff & data[i++];
            sb.append(base64EncodeChars[c >> 2]);
            sb.append(base64EncodeChars[(c & 0x03) << 4]);
            sb.append("==");
        }
        else if (r == 2) {
            c = (0x000000ff & data[i++]) << 8 |
                (0x000000ff & data[i++]);
            sb.append(base64EncodeChars[c >> 10]);
            sb.append(base64EncodeChars[c >> 4 & 0x3f]);
            sb.append(base64EncodeChars[(c & 0x0f) << 2]);
            sb.append("=");
        }
        return sb.toString();
	}
}
