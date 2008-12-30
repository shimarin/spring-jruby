package net.stbbs.spring.jruby;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.stbbs.spring.jruby.modules.ModuleException;

import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class InstanceEvalServlet extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	WebApplicationContext wac;
	SpringIntegratedJRubyRuntime ruby;
	
	@Override
	public void init() throws ServletException
	{
		wac = WebApplicationContextUtils.getRequiredWebApplicationContext(this.getServletContext());
		ruby = SpringIntegratedJRubyRuntime.init(wac);
		try {
			ruby.defineVariousModules();
		} catch (ModuleException ex) {
			throw new ServletException("Modules couldn't load", ex);
		}
		ruby.loadRubyGems();
		RubyClass clazz = ruby.defineApplicationContextClass("ApplicationContext");
		clazz.defineMethod("servletContext", new Callback(){
			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}

			public IRubyObject execute(IRubyObject arg0, IRubyObject[] arg1,
					Block arg2) {
				return ruby.toRuby(getServletContext());
			}
		});
		try {
			ruby.evalInClass(clazz, "WEB-INF/instanceEvalServlet.rb", false);
		} catch (IOException e) {
			throw new ServletException(e);
		}
		
		ruby.getString().defineMethod("inspect", new StringInspect());
		ruby.setGetBytes();	// getBytesを使えるようにする
	}
	
	static protected String escapeHTML(String input, boolean lf2br){

        StringBuffer sb = new StringBuffer();

        if( input == null ) return "";

        for(int i=0;i<input.length();i++){
            if( input.charAt(i) == '<' ){
                sb.append( "&lt;" );
            } else if( input.charAt(i) == '>' ){
                sb.append( "&gt;" );
            } else if( input.charAt(i) == '"' ){
                sb.append( "&quot;" );
            } else if( input.charAt(i) == '&' ){
                sb.append( "&amp;" );
            } else if (input.charAt(i) == '\n' && lf2br) {
        		sb.append( "<br>\n");
            } else {
                sb.append( input.charAt(i) );
            }
        }

        return sb.toString();
	}
	
	static protected String escapeHTML(String input)
	{
		return escapeHTML(input, false);
	}
	
	protected void printForm(PrintWriter out, String expression)
	{
		out.println("<h1>expression</h1>");
		out.println("<form method='post'>");
		out.print("<textarea cols='80' rows='12' name='expression'>");
		out.print(escapeHTML(expression));
		out.println("</textarea><br>");
		out.println("<input type='submit' value='eval'>");
		out.println("</form>");
	}
	
	static protected String obj2str(Object obj)
	{
		if (obj == null) return "nil";
		if (obj instanceof RubyString) {
			return '"' + ((RubyString)obj).getUnicodeValue() + '"';
		}
		if (obj instanceof RubyObject) {
			return ((RubyObject)obj).inspect().asString().getUnicodeValue();
		}
		return obj.toString();
	}
	
	static protected void printSqlRowSet(PrintWriter out, SqlRowSet rs)
	{
		out.println("<table border='1'>");
		SqlRowSetMetaData md = rs.getMetaData();
		String[] cols = md.getColumnNames();
		out.println("<tr>");
		for (String col:cols) {
			out.print("<th>" + escapeHTML(col) + "</th>");
		}
		out.println("</tr>");
		while (rs.next()) {
			out.println("<tr>");
			for (int i = 1; i <= cols.length; i++) {
				Object obj = rs.getObject(i);
				out.print("<td>" + (obj != null? escapeHTML(obj.toString()):"null") + "</td>");
			}
			out.println("</tr>");
		}
		out.println("</table>");
	}
	
	static protected void printTableDescription(PrintWriter out, TableDescription td)
	{
		out.println("<h3>Table: " + escapeHTML(td.getTableName()) + "</h3>");
		out.println("<table border='1'>");
		out.println("<tr><th>name</th><th>type</th><th>precision</th></tr>");
		for (ColumnDescription col:td.getColumns()) {
			out.println("<tr>");
			out.println("<td>" + escapeHTML(col.getName()) + "</td>");
			out.println("<td>" + escapeHTML(col.getType()) + "</td>");
			out.println("<td>" + col.getPrecision()+ "</td>");
			out.println("</tr>");
		}
		out.println("</table>");
	}
	
	protected void printResult(PrintWriter out, Object result)
	{
		out.println("<h1>result</h1>");
		if (result == null) {
			out.println("nil");
			return;
		}

		if (result instanceof RubyObject) {
			RubyObject ro = (RubyObject)result;
			Object jo = ruby.toJava(ro);
			if (jo instanceof SqlRowSet) {
				printSqlRowSet(out, (SqlRowSet)jo);
			} else if (jo instanceof TableDescription) {
				printTableDescription(out, (TableDescription)jo);
			} else {
				out.println(escapeHTML(obj2str(result) ) );
			}
			return;
		}
		
		if (result instanceof Collection) {
			Collection col = (Collection)result;
			for (Object obj:col) {
				out.println(escapeHTML(obj2str(obj)) + "<br>");
			}
			return;
		}
		
		if (result.getClass().isArray()) {
			Object[] col = (Object[])result;
			for (Object obj:col) {
				out.println(escapeHTML(obj2str(obj)) + "<br>");
			}
			return;
		}
		
		if (result instanceof Map) {
			Map map = (Map)result;
			for (Object obj:map.entrySet()) {
				Map.Entry entry = (Map.Entry)obj;
				out.println(escapeHTML(obj2str(entry.getKey())) + " => " + escapeHTML(obj2str(entry.getValue())) + "<br>");
			}
			return;
		}

		
		out.println(escapeHTML(obj2str(result)));
	}
	
	protected void printException(PrintWriter out, RubyException ex) throws IOException
	{
		out.println("<h1>exception</h1>");
		out.println(escapeHTML(ex.message.toString()));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		ex.printBacktrace(ps);
		ps.close();
		baos.flush();
		out.println(escapeHTML(baos.toString("UTF-8"), true));
		baos.close();
	}
	
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String expression = request.getParameter("expression");

		IRubyObject result = null;
		RubyException re = null;
		boolean exec = false;
		try {
			if (expression != null) {
				IRubyObject instanceEvaluableInstance = ruby.allocate("ApplicationContext");
				instanceEvaluableInstance.setInstanceVariable("@applicationContext",
						JavaEmbedUtils.javaToRuby(instanceEvaluableInstance.getRuntime(), wac));
				result = instanceEvaluableInstance.callMethod(
					ruby.getCurrentContext(), "instance_eval", 
					ruby.newString(expression));
				exec = true;
			}
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
		
		// ダウンロードの場合の処理
		if (exec) {
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
		
		printForm(out, expression);

		if (exec) {
			printResult(out, result);
		}
		if (re != null) {
			printException(out, re);
		}
		
		out.println("<body></html>");
	}

	static protected class StringInspect implements Callback {

		public IRubyObject execute(IRubyObject self, IRubyObject[] arg1,
				Block arg2) {
			String str = self.asString().getUnicodeValue(); 
			return RubyString.newUnicodeString(self.getRuntime(), ('"' + str + '"'));
		}

		public Arity getArity() {
			return Arity.noArguments();
		}
		
	}

}
