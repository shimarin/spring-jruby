package net.stbbs.jruby.modules;

import java.io.InputStream;
import java.util.Collection;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.DOMReader;
import org.dom4j.io.SAXReader;
import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class Dom4jSupport {
	
	private Ruby runtime;

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, Dom4jDocumentDecorator.class);
		Util.registerDecorator(runtime, DOMDocumentDecorator.class);
	}
	
	public Dom4jSupport(Ruby runtime) {
		this.runtime = runtime;
	}

	@JRubyMethod(required=1)
	public Document parseXML(IRubyObject self, IRubyObject[] args, Block block) throws DocumentException
	{
		InputStream in = (InputStream)JavaEmbedUtils.rubyToJava(runtime, args[0], InputStream.class);
		SAXReader reader = new SAXReader();
		return reader.read(in);
	}

	@Decorator(org.dom4j.Document.class)
	static public class Dom4jDocumentDecorator {

		@JRubyMethod(required=1)
		public Collection selectNodes(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			Document doc = (Document)JavaEmbedUtils.rubyToJava(runtime, self, Document.class);
			return doc.getRootElement().selectNodes(args[0].asString().getUnicodeValue());
		}
		
		@JRubyMethod(required=1)
		public Node selectSingleNode(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			Document doc = (Document)JavaEmbedUtils.rubyToJava(runtime, self, Document.class);
			return doc.getRootElement().selectSingleNode(args[0].asString().getUnicodeValue());
		}
	}

	@Decorator(org.w3c.dom.Document.class)
	public static class DOMDocumentDecorator {
		@JRubyMethod
		public Document dom4j(IRubyObject self, IRubyObject[] args, Block block) throws DocumentException
		{
			Ruby runtime = self.getRuntime();
			DOMReader reader = new DOMReader();
			return reader.read((org.w3c.dom.Document)JavaEmbedUtils.rubyToJava(runtime, self, org.w3c.dom.Document.class));
		}
	}
}
