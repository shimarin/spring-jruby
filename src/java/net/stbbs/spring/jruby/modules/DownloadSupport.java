package net.stbbs.spring.jruby.modules;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
	
	public static class DownloadContent {
		
		private String contentType = null;
		private byte[] content;

		public DownloadContent(String contentType, byte[] content)
		{
			this.contentType = contentType;
			this.content = content;
		}

		public DownloadContent(byte[] content)
		{
			this.content = content;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public void setContent(byte[] content) {
			this.content = content;
		}

		public String getContentType() throws IOException {
			if (contentType == null) {
				ByteArrayInputStream is = new ByteArrayInputStream(content);
				String guessedContentType = URLConnection.guessContentTypeFromStream(is);
				is.close();
				return guessedContentType;
			}
			return contentType;
		}

		public int getContentLength() {
			return content.length;
		}

		public void out(OutputStream out) throws IOException {
			out.write(content);
		}
		
		public DownloadContent zip(String filename) throws IOException
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ZipOutputStream zout = new ZipOutputStream(baos);
			zout.putNextEntry(new ZipEntry(filename));
			zout.write(content);
			zout.closeEntry();
			zout.close();
			return new DownloadContent("application/zip", baos.toByteArray());
		}

	}

}
