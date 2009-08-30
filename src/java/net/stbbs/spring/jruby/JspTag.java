package net.stbbs.spring.jruby;

import java.io.IOException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyTagSupport;

import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;

import org.jruby.runtime.builtin.IRubyObject;

public class JspTag extends BodyTagSupport {

	private static final long serialVersionUID = -4259416464801934061L;
	static private final String PROXY_OBJECT_NAME = "__spring_jruby_proxy_object";
	
	private String var;
	private String expr;
	private Boolean escapeXml;
	
	public void setVar(String var) {
		this.var = var;
	}

	public void setExpr(String expr) {
		this.expr = expr;
	}

	public void setEscapeXml(Boolean escapeXml) {
		this.escapeXml = escapeXml;
	}

	public int doEndTag() throws JspException
	{
		if (expr == null) {
			expr = this.getBodyContent().getString();
		}

		IRubyObject proxyObject = (IRubyObject) pageContext.getAttribute(PROXY_OBJECT_NAME, PageContext.REQUEST_SCOPE);
		if (proxyObject == null) {
			IRubyObject aco = JRubyRuntimeListener.getApplicationContextObject(pageContext.getServletContext());
			proxyObject = aco.callMethod(aco.getRuntime().getCurrentContext(), "allocateProxyObject");
		}
		IRubyObject result = ApplicationContextSupport.scopedInstanceEval(proxyObject, expr);
		Object obj = Util.convertRubyToJava(result);

		if (var != null) {
			pageContext.setAttribute(var, obj, PageContext.REQUEST_SCOPE);
		} else {
			try {
				String out = obj.toString();
				if (escapeXml == null || escapeXml == true) {
					out = InstanceEvalService.escapeHTML(out);
				}
				pageContext.getOut().write(out);
			} catch (IOException e) {
				throw new JspException(e);
			}
		}
		
		return EVAL_PAGE;
	}

}
