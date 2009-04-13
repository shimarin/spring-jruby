package net.stbbs.spring.jruby;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JSONRPCServlet extends HttpServlet {
	private InstanceEvalService svc;
	@Override
	public void init() throws ServletException
	{
		try {
			svc = new JSONRPCService();
		}
		catch (java.lang.NoClassDefFoundError ex) {
			throw new ServletException("JRuby1.0がクラスパスに存在しません。WEB-INF/libに配置してください。:" + ex.getMessage());
		}
		try {
			svc.init(getServletContext());
		}
		catch (java.lang.NoClassDefFoundError ex) {
			throw new ServletException("必要なライブラリ(backport-util-concurrent,asm2,spring)がクラスパスに存在しません。WEB-INF/libに配置してください。:" + ex.getMessage());
		} catch (IOException e) {
			throw new ServletException(e);
		}
	}

	@Override
	public void destroy()
	{
		svc.destroy(getServletContext());
	}
	
	
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		svc.service(request, response);
	}
}
