package net.stbbs.jruby.modules;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DOMSupport {
	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, NodeDecorator.class);
		Util.registerDecorator(runtime, NodeListDecorator.class);
	}
	
	@Decorator(NodeList.class)
	public static class NodeListDecorator {
		NodeList nodeList;
		public NodeListDecorator(NodeList nodeList)
		{
			this.nodeList = nodeList;
		}
		
		public static void onRegister(RubyModule module)
		{
			module.include(new IRubyObject[] {module.getRuntime().getModule("Enumerable")});
		}

		@JRubyMethod
		public IRubyObject each(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			for (int i = 0; i < nodeList.getLength(); i++) {
				block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, nodeList.item(i))});
			}
			return self;
		}
	}

	@Decorator(Node.class)
	public static class NodeDecorator {
		Node node;
		XPathFactory factory;
		public NodeDecorator(Node node)
		{
			this.node = node;
			this.factory = XPathFactory.newInstance();
		}
		
		@JRubyMethod
		public IRubyObject collect(IRubyObject self, IRubyObject[] args, Block block) throws XPathExpressionException
		{
			Ruby runtime = self.getRuntime();
			NodeList nl = select(self, args, block);
			return JavaEmbedUtils.javaToRuby(runtime, nl).callMethod(runtime.getCurrentContext(), "collect", block);
		}

		@JRubyMethod
		public IRubyObject each(IRubyObject self, IRubyObject[] args, Block block) throws XPathExpressionException
		{
			Ruby runtime = self.getRuntime();
			NodeList nl = select(self, args, block);
			JavaEmbedUtils.javaToRuby(runtime, nl).callMethod(runtime.getCurrentContext(), "each", block);
			return self;
		}
		
		@JRubyMethod
		public NodeList select(IRubyObject self, IRubyObject[] args, Block block) throws XPathExpressionException
		{
			Ruby runtime = self.getRuntime();
			NodeList nl;
			if (args.length > 0) {
				XPathExpression expr = factory.newXPath().compile(args[0].asString().getUnicodeValue());
				return (NodeList)expr.evaluate(node, XPathConstants.NODESET);
			} else {
				return node.getChildNodes();
			}
		}

		@JRubyMethod(name="[]", required=1)
		public IRubyObject get(IRubyObject self, IRubyObject[] args, Block block) throws XPathExpressionException
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				runtime.newArgumentError(args.length, 1);
			}
			XPathExpression expr = factory.newXPath().compile(args[0].asString().getUnicodeValue());
			Node node =  (Node)expr.evaluate(this.node, XPathConstants.NODE);
			switch (node.getNodeType()) {
			case Node.ATTRIBUTE_NODE:
				return RubyString.newUnicodeString(runtime, node.getTextContent());
			case Node.TEXT_NODE:
				return RubyString.newUnicodeString(runtime, node.getTextContent());
			default:
				return JavaEmbedUtils.javaToRuby(runtime, node);
			}
		}
		
		@JRubyMethod
		public String to_s(IRubyObject self, IRubyObject[] args, Block block)
		{
			return node.getTextContent();
		}		
	}
	
}
