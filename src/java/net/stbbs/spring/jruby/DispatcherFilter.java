package net.stbbs.spring.jruby;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.stbbs.spring.jruby.modules.MVCSupport;

import org.jruby.RubyException;
import org.jruby.RubyProc;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class DispatcherFilter  implements Filter {
	
	WebApplicationContext wac;
	FilterConfig filterConfig;

	Map<String,String> actions;
	String scriptRoot;
	String defaultAction;
	String initScript;

	protected SpringIntegratedJRubyRuntime getRuby()
	{
		return (SpringIntegratedJRubyRuntime)filterConfig.getServletContext().getAttribute("ruby");
	}
	
	public void init(FilterConfig config) throws ServletException {
		this.filterConfig = config;
		/*
		config.getInitParameter("actionBeanIdPrefix");
		*/
		wac = WebApplicationContextUtils.getRequiredWebApplicationContext(config.getServletContext());
	}

	/*
	protected Object invoke(Action action, String methodName, String[] pathParams, HttpServletRequest request, HttpServletResponse response)
	{
		action.instance_variable_set("@request", request);
		action.instance_variable_set("@response", response);
		action.instance_variable_set("@pathParams", pathParams);
		Map<String,String[]> paramMap = request.getParameterMap();
		Map<String, Object> dstMap = new HashMap<String, Object>();
		for (Map.Entry<String, String[]> entry:paramMap.entrySet()) {
			String[] values = entry.getValue();
			dstMap.put(entry.getKey(), values.length == 1? values[0] : values);
		}
		action.instance_variable_set("@params", dstMap);
		action.instance_variable_set("@session", request.getSession());
		return action.send(methodName);
	}
*/
	String[] tokenizePath(String servletPath)
	{
		Collection<String> pathBlocks = new ArrayList<String>();
		
		int i = 0;
		if (servletPath.charAt(0) == '/') i = 1;
		StringBuffer buf = new StringBuffer();
		while (i < servletPath.length()) {
			char c = servletPath.charAt(i++);
			if (c == '/') {
				pathBlocks.add(buf.toString());
				buf = new StringBuffer();
				continue;
			}
			buf.append(c);
		}
		pathBlocks.add(buf.toString());
		
		String[] results = new String[pathBlocks.size()];
		i = 0;
		for (String s:pathBlocks) { 
			results[i] = s; 
			i++; 
		}
		return results;
	}
	
	public void printInstanceVariables(IRubyObject obj, Writer out) throws IOException
	{
		Map instanceVariables = obj.getInstanceVariables();
		out.write("<table border='1'>\n");
		for (Object o:instanceVariables.entrySet()) {
			String name = (String)((Map.Entry)o).getKey();
			out.write("<tr>\n");
			out.write("<td>\n");
			out.write(InstanceEvalServlet.escapeHTML(name, true));
			out.write("</td>\n");
			out.write("<td>\n");
			out.write(InstanceEvalServlet.escapeHTML(InstanceEvalServlet.obj2str(((Map.Entry)o).getValue()), true));
			out.write("</td>\n");
			out.write("</tr>\n");
		}
		out.write("</table>\n");
	}
	
	private String getViewResourceName(String controllerName, String viewName, int depth)
	{
		return scriptRoot + "/"
			+ (controllerName != null? (controllerName + '/') : "") 
			+ (depth > 0? (Integer.toString(depth) + '/') : "")
			+ viewName + ".jsp";
		
	}
	
	private String getScriptResourceName(String controllerName, String actionName, int depth)
	{
		return scriptRoot + "/" 
			+ (controllerName != null? (controllerName + '/') : "")
			+ (depth > 0? (Integer.toString(depth) + '/') : "")
			+ actionName + ".rb";
	}
	
	protected void printResults(IRubyObject self, Object result, Writer out) throws IOException
	{
		if (result instanceof Exception) {
			Exception ex = (Exception)result;
			out.write("<h2>exception</h2>\n");
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(baos);
			if (ex instanceof RaiseException) {
				RubyException rex = ((RaiseException)ex).getException();
				if (rex != null) {
					ps.println(InstanceEvalServlet.escapeHTML(rex.message.toString()));
					rex.printBacktrace(ps);
				}
			} else {
				ps.println("<pre>");
				ex.printStackTrace(ps);
				ps.println("</pre>");
			}
			ps.close();
			baos.flush();
			out.write(InstanceEvalServlet.escapeHTML(baos.toString("UTF-8"), true) + '\n');
			baos.close();
		} else {
			out.write("<h2>Return value</h2>\n");
		}
		out.write("<h2>Instance variables</h2>\n");
		printInstanceVariables(self, out);
	}
	
	public void doFilter(
		ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException 
	{
		HttpServletRequest request = (HttpServletRequest)req;
		HttpServletResponse response = (HttpServletResponse)res;

		request.setCharacterEncoding("UTF-8");

		SpringIntegratedJRubyRuntime ruby = getRuby();
		MVCSupport mod = (MVCSupport)ruby.getModule("MVCSupport");
		if (mod == null) {
			chain.doFilter(req, res);
			return;
		}
		
		RubyProc proc = mod.getController(request.getServletPath());
		if (proc == null) {
			chain.doFilter(req, res);
			return;
		}
		
		IRubyObject instanceEvaluableInstance = ruby.allocate("ApplicationContext");
		RubyException re = null;
		IRubyObject result = null;
		try {
			result = proc.call(new IRubyObject[0], instanceEvaluableInstance, null);
		}
		catch (RaiseException ex) {
			re = ex.getException();
		}
		catch (RuntimeException ex) {
			if (ex.getCause() instanceof RaiseException) {
				re = ((RaiseException)ex.getCause()).getException();
			} else {
				throw ex;
			}
		}
		
		if (result != null) {
			Object o = ruby.toJava(result);
			if (o instanceof DownloadContent) {
				DownloadContent dc = (DownloadContent)o;
				response.setContentType(dc.getContentType());
				response.setContentLength(dc.getContentLength());
				ServletOutputStream out = response.getOutputStream();
				dc.out(out);
				return;
			}
		}

		response.setContentType("text/html; charset=UTF-8");
		PrintWriter out = response.getWriter();
		
		out.println("<html><body>");
		if (result != null) InstanceEvalServlet.printResult(out, ruby, result);
		if (re != null) 	InstanceEvalServlet.printException(out, re);
		out.println("<body></html>");

	}

	public void destroy() {
		// TODO Auto-generated method stub
	}


}
