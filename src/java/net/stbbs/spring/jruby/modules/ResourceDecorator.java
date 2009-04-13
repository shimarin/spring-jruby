package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.stbbs.jruby.Decorator;

import org.jruby.Ruby;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.core.io.Resource;

@Decorator(Resource.class)
public class ResourceDecorator {

	private Resource resource;
	
	public ResourceDecorator(Resource resource)
	{
		this.resource = resource;
	}
	
	@JRubyMethod
	public IRubyObject withInputStream(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		if (!resource.isReadable()) return runtime.getNil();
		InputStream is = resource.getInputStream();
		if (is == null) return runtime.getNil();
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
		return runtime.getNil();	// block not given
	}
	
	@JRubyMethod
	public String read(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		if (!resource.isReadable()) return null;
		byte[] bytes = this.toByteArray();
		return new String(bytes, "UTF-8");
	}
	
	@JRubyMethod
	public byte[] toByteArray(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		return toByteArray();
	}
	
	private byte[] toByteArray() throws IOException
	{
		if (!resource.isReadable()) return null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		InputStream is = resource.getInputStream();
		try {
			int n;
			while ((n = is.read(buf)) >= 0) {
				baos.write(buf, 0, n);
			}
		}
		finally {
			is.close();
		}
		return baos.toByteArray();
	}
	
	@JRubyMethod
	public DownloadContent download(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		String contentType = args.length > 0? args[0].asString().getUnicodeValue() : null;
		return new DownloadContent(contentType, toByteArray());
	}
}
