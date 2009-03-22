package net.stbbs.spring.jruby.modules;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.stbbs.jruby.Decorator;
import net.stbbs.spring.jruby.modules.DownloadSupport.DownloadContent;

import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;

@Decorator(Resource.class)
public class ResourceDecorator {

	@JRubyMethod
	public IRubyObject withInputStream(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		InputStream is = ((Resource)JavaEmbedUtils.rubyToJava(runtime, self, Resource.class)).getInputStream();

		try {
			if (block.isGiven()) {
				return block.call(
					runtime.getCurrentContext(), 
					new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, is)});
			}
		}
		finally {
			if (is != null) {
				is.close();
			}
		}
		return runtime.getNil();
	}
	
	@JRubyMethod
	public String read(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		InputStream is = null;
		StringBuffer buf = new StringBuffer();
		try {
			is = ((Resource)JavaEmbedUtils.rubyToJava(runtime, self, Resource.class)).getInputStream();
			InputStreamReader insr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(insr);
			String line = null;
			while ((line = br.readLine()) != null) {
				buf.append(line);
				buf.append('\n');
			}
			return buf.toString(); 
		}
		finally {
			if (is != null) {
				is.close();
			}
		}
	}
	
	@JRubyMethod(required=1)
	public DownloadContent download(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		// 引数が０の場合エラー
		if (args.length < 1) {
			throw self.getRuntime().newArgumentError("Method requires at least one argument.");
		}
		String contentType = "text/plain";
		if (args.length > 0) {
			contentType = args[0].asString().getUnicodeValue();
		}
		Resource r = (Resource)JavaEmbedUtils.rubyToJava(runtime, self, Resource.class);
		InputStream is = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		try {
			is = r.getInputStream();
			int n;
			while ((n = is.read(buf)) >= 0) {
				baos.write(buf, 0, n);
			}
		}
		finally {
			if (is != null) {
				is.close();
			}
		}
		return new DownloadContent(contentType, baos.toByteArray());
	}
}
