package net.stbbs.spring.jruby;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jruby.Ruby;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import net.arnx.jsonic.JSON;
import net.arnx.jsonic.JSONException;

public class JSONRPCService extends InstanceEvalService {

	protected Object invoke(String beanName, String methodName, List params) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
	{
		IRubyObject proxy = this.getProxyInstance();
		Ruby runtime = proxy.getRuntime();
		IRubyObject[] args = new IRubyObject[params.size()];
		for (int i = 0; i < args.length; i++) {
			args[i] = JavaEmbedUtils.javaToRuby(runtime, params.get(i));
		}
		ThreadContext context = runtime.getCurrentContext();
		IRubyObject rbean = proxy.callMethod(context, beanName);
		/*
		Object bean = JavaEmbedUtils.rubyToJava(runtime, rbean, null);
		if (!(bean instanceof IRubyObject)) {
            MethodMatcher methodMatcher = new MethodMatcher();
            Method method = methodMatcher.getMethod(bean.getClass(), methodName, params);
            return method.invoke(bean, params.toArray());
		}*/
		if (rbean.getMetaClass().getSuperClass().method_defined(runtime.newSymbol(methodName)).isTrue()) {
			throw runtime.newSecurityError("Calling method " + methodName + " is not allowed");
		}
		return  rbean.callMethod(proxy.getRuntime().getCurrentContext(), methodName, args);
	}
	
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		int errorCode = 0;
		String errorMessage = null;
		Throwable throwable = null;
		Object result = null;
		Map<String,Object> req = null;
		String beanName = request.getPathInfo();
		if (beanName != null) {
			if (beanName.startsWith("/")) beanName = beanName.substring(1);
		}
		if (beanName == null || beanName.equals("")) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		try {
			req = (Map<String,Object>) JSON.decode(request.getInputStream());
			if (req == null || !req.containsKey("method") || !req.containsKey("params")) {
				throwable = new Throwable();
				errorCode = -32600;
				errorMessage = "Invalid Request.";
			} else {
				String method = (String)req.get("method");
				List params = (List)req.get("params");
				result = this.invoke(beanName, method, params);
			}
		}
		catch (JSONException e) {
			throwable = e;
			if (e.getErrorCode() == JSONException.POSTPARSE_ERROR) {
				errorCode = -32602;
				errorMessage = "Invalid params.";
			} else  {
				errorCode = -32700;
				errorMessage = "Parse error.";
			}
		}
		catch (InvocationTargetException e) {
			throwable = e.getTargetException();
			errorCode = -32603;
			errorMessage = e.getTargetException().getMessage();
		}
		catch (IllegalStateException e) {
			errorCode = -32601;
			errorMessage = "Method not found.";
		}
		catch (IllegalArgumentException e) {
			errorCode = -32602;
			errorMessage = "Invalid params.";
		}
		catch (RaiseException e) {
			if (e.getException() instanceof org.jruby.RubyNoMethodError) {
				throwable = e;
				errorCode = -32601;
				errorMessage = "Method not found.";			
			} else {
				throwable = e;
				errorCode = -32603;
				errorMessage = "JRuby error:" + e.getException().message.asString().getUnicodeValue();
			}
		}
		catch (Exception e) {
			throwable = e;
			errorCode = -32603;
			errorMessage = "Internal error." + e.getMessage();
		}

		// it's notification when id was null
		if (req != null && req.containsKey("method") && req.containsKey("params") && !req.containsKey("id")) {
			response.setStatus(HttpServletResponse.SC_ACCEPTED);
			return;
		}

		response.setContentType("application/json");
		Map<String, Object> res = new LinkedHashMap<String, Object>();
		res.put("result", result);
		if (errorCode == 0) {
			res.put("error", null);
		} else {
			Map<String, Object> error = new LinkedHashMap<String, Object>();
			error.put("code", errorCode);
			error.put("message", errorMessage);
			error.put("data", throwable);
			res.put("error", error);
		}
		res.put("id", (req != null) ? req.get("id") : null);
		JSON.encode(res, response.getOutputStream());
	}
}
