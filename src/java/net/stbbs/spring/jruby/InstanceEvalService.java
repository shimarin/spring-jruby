package net.stbbs.spring.jruby;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.stbbs.spring.jruby.modules.ApplicationContextDecorator;
import net.stbbs.spring.jruby.modules.ApplicationContextSupport;
import net.stbbs.spring.jruby.modules.RequestContextSupport;
import net.stbbs.spring.jruby.modules.DownloadSupport.DownloadContent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyException;
import org.jruby.RubyNil;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;

public class InstanceEvalService {
	protected IRubyObject applicationContext;
	private Log logger;	
	
	protected void init(ServletContext servletContext) throws ServletException, IOException
	{
		try {
			logger = LogFactory.getLog(InstanceEvalService.class);
		}
		catch (java.lang.NoClassDefFoundError ex) {
			throw new ServletException("commons-loggingがクラスパスに存在しません。WEB-INF/libに配置してください。:" + ex.getMessage());
		}

		applicationContext = JRubyRuntimeListener.getApplicationContextObject(servletContext);
		if (applicationContext == null) {
			applicationContext = JRubyRuntimeListener.initializeRuntime(servletContext);
		}
	}
	
	protected void destroy(ServletContext servletContext)
	{
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
		out.println("<h2>expression</h2>");
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

	/**
	 * 知らないJavaオブジェクトをそれなりに見やすく表示する
	 * @param out
	 * @param obj
	 */
	static protected void prettyPrint(PrintWriter out, Object obj)
	{
		if ((obj instanceof String) || (obj instanceof Number)) {
			out.println(escapeHTML(obj.toString()) + "<br>");
			return;
		}

		// getterで読み出せるプロパティ全て
		PropertyDescriptor[] props = BeanUtils.getPropertyDescriptors(obj.getClass());
		// publicフィールド全て
		Field[] fields = obj.getClass().getFields();

		out.println(escapeHTML( obj.toString() ) + "<br>");
		out.println("<table border='1'>");
		for (PropertyDescriptor pd:props) {
			Method m = pd.getReadMethod();
			if (m != null) {
				Object value;
				try {
					value = m.invoke(obj);
				} catch (IllegalArgumentException e) {
					continue;
				} catch (IllegalAccessException e) {
					continue;
				} catch (InvocationTargetException e) {
					continue;
				}
				out.println("<tr>");
				out.println("<th>" + escapeHTML(pd.getDisplayName()) + "</th><td>" + escapeHTML( obj2str(value))  + "</td>");
				out.println("</tr>");
			}
		}
		for (Field f:fields) {
			int modifiers = f.getModifiers();
			if ((modifiers & Modifier.PUBLIC) > 0 && (modifiers & Modifier.STATIC) == 0) {
				Object value;
				try {
					value = f.get(obj);
				} catch (IllegalArgumentException e1) {
					continue;
				} catch (IllegalAccessException e1) {
					continue;
				}
				out.println("<tr>");
				out.println("<th>" + escapeHTML(f.getName()) + "</th><td>" + escapeHTML( obj2str(value) ) + "</td>");
				out.println("</tr>");
			}
		}
		
		out.println("</table>");
		//out.println(escapeHTML( obj2str(obj) ));
	}
	
	static protected void printResult(PrintWriter out, Ruby ruby, 
			Object result)
	{
		out.print("<p>");
		try {
			if (result == null || result instanceof RubyNil) {
				out.println("nil<br>");
				return;
			}
			
			if (result instanceof RubyString) {
				out.println(escapeHTML(((RubyString)result).getUnicodeValue() ) + "<br>" );
				return;
			}
	
			if (result instanceof RubyObject) {
				RubyObject ro = (RubyObject)result;
				result = JavaEmbedUtils.rubyToJava(ruby, ro, null);
				// Javaオブジェクトなんだけど何故かto_assocメソッドを持っている場合はそれを使う
				if (result != null && !(result instanceof RubyObject) && ro.respondsTo("to_assoc")) {
					out.println(
						escapeHTML(
							ro.callMethod(ro.getRuntime().getCurrentContext(), "to_assoc").inspect().asString().getUnicodeValue()
						) + "<br>");
					return;
				}
			}
			
			if (result instanceof RubyObject) {	// 皮をむいてもRubyObectなのでpして終わる
				out.println(escapeHTML(obj2str(result) ) + "<br>" );
				return;
			} 
			
			if (result instanceof SqlRowSet) {
				printSqlRowSet(out, (SqlRowSet)result);
				return;
			} 
			
			if (result instanceof TableDescription) {
				printTableDescription(out, (TableDescription)result);
				return;
			} 
			
			if (result instanceof Collection) {
				Collection col = (Collection)result;
				out.println("Collection(count=" + col.size() + ")<br>");
				for (Object obj:col) {
					prettyPrint(out, obj);
					out.println("<br>");
				}
				return;
			}
			
			if (result.getClass().isArray()) {
				Object[] col = (Object[])result;
				out.println("Array(count=" + col.length + ")<br>");
				for (Object obj:col) {
					prettyPrint(out, obj);
					out.println("<br>");
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
			
			prettyPrint(out, result);
		}
		finally {
			out.print("</p>");
		}
	}
	
	static protected void printException(PrintWriter out, RubyException ex) throws IOException
	{
		out.println("<h2>exception</h2>");
		out.println(escapeHTML(ex.message.toString()));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		ex.printBacktrace(ps);
		ps.close();
		baos.flush();
		out.println(escapeHTML(baos.toString("UTF-8"), true));
		baos.close();
	}

	public IRubyObject doInstanceEval(String expression)
	{
		IRubyObject instanceEvaluableInstance = applicationContext.callMethod(
				applicationContext.getRuntime().getCurrentContext(), "allocateProxyObject");
		return ApplicationContextSupport.scopedInstanceEval(instanceEvaluableInstance, expression);
	}
	
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		request.setCharacterEncoding("UTF-8");
		String expression = request.getParameter("expression");

		IRubyObject result = null;
		RubyException re = null;
		boolean exec = false;
		try {
			if (expression != null) {
				result = doInstanceEval(expression);
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
			Object o = result != null? JavaEmbedUtils.rubyToJava(result.getRuntime(), result, null) : null;
			DownloadContent dc = null;
			if (o instanceof DownloadContent) {
				dc = (DownloadContent)o;
			} else if (o instanceof byte[]) {
				dc = new DownloadContent((byte[])o);
			}
			if (dc != null) {
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

		List<IRubyObject> ruby_p = RequestContextSupport.getRubyPObjects();
		if (ruby_p != null) {
			out.println("<h2>p</h2>");
			for (IRubyObject ro:ruby_p) {
				printResult(out, ro.getRuntime(), ro);
			}
		}
		if (exec) {
			out.println("<h2>result</h2>");
			printResult(out, result.getRuntime(), result);
		}
		if (re != null) {
			printException(out, re);
		}
		
		out.println("<body></html>");
	}
}
