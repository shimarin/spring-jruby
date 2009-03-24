package net.stbbs.jruby.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class URLConnectionSupport {
	
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, URLConnectionDecorator.class);
	}
	
	@JRubyMethod(required=1)
	public URLConnection openConnection(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		Ruby runtime = self.getRuntime();
		if (args.length < 1) {
			throw runtime.newArgumentError(args.length, 1);
		}
		URL url = new URL(args[0].asString().getUnicodeValue());
		return url.openConnection();
	}

	@Decorator(URLConnection.class)
	public static class URLConnectionDecorator {
		private URLConnection conn;
		public URLConnectionDecorator(URLConnection conn)
		{
			this.conn = conn;
		}
		
		@JRubyMethod(name="basic_auth_header=", required=1)
		public void basic_auth_header_eq(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException
		{
			setBasicAuthHeader(self, args, block);
		}
		
		@JRubyMethod(required=1, optional=1)
		public void setBasicAuthHeader(IRubyObject self, IRubyObject[] args, Block block) throws UnsupportedEncodingException
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw runtime.newArgumentError(args.length, 1);
			}
			String username = null;
			String password = null;
			String encoded = null;
			if (args[0] instanceof RubyArray) {
				RubyArray array = (RubyArray)args[0];
				username = array.at(runtime.newFixnum(0)).asString().getUnicodeValue();
				password = array.at(runtime.newFixnum(1)).asString().getUnicodeValue();
			} else {
				if (args.length < 2) {
					encoded = args[0].asString().getUnicodeValue();
				} else {
					username = args[0].asString().getUnicodeValue();
					password = args[1].asString().getUnicodeValue();
				}
			}
			if (encoded == null) {
				encoded = JavaTypeSupport.encodeBase64((username + ':' + password).getBytes("UTF-8"));
			}
			conn.setRequestProperty("Authorization", "Basic " + encoded);
		}
		
		@JRubyMethod
		public IRubyObject withInputStream(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			if (!block.isGiven()) return runtime.getNil();
			InputStream is = conn.getInputStream();
			try {
				return block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, is)});
			}
			finally {
				is.close();
			}
		}

		@JRubyMethod
		public IRubyObject withOutputStream(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			if (!block.isGiven()) return runtime.getNil();
			OutputStream os = conn.getOutputStream();
			try {
				return block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, os)});
			}
			finally {
				os.close();
			}
		}

		@JRubyMethod
		public String read(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			InputStream is = conn.getInputStream();
			try {
				return new String(InputStreamSupport.toByteArray(is), "UTF-8");
			}
			finally {
				is.close();
			}
		}
		
		@JRubyMethod
		public Document readXML(IRubyObject self, IRubyObject[] args, Block block) throws IOException, ParserConfigurationException, SAXException
		{
			DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dbfactory.newDocumentBuilder();
			InputStream is = conn.getInputStream();
			try {
				return builder.parse(is);
			}
			finally {
				is.close();
			}
		}

		@JRubyMethod
		public byte[] toByteArray(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			InputStream is = conn.getInputStream();
			try {
				return InputStreamSupport.toByteArray(is);
			}
			finally {
				is.close();
			}
		}

		
	}
}
