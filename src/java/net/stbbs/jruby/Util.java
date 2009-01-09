package net.stbbs.jruby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyNil;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.RubyTime;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class Util {
	/**
	 * Rubyオブジェクトのディープコンバートを行う
	 * @param obj
	 * @return
	 */
	static public Object convertRubyToJava(Object obj)
	{
		if (!(obj instanceof IRubyObject)) return obj;

		if (obj instanceof RubyNil) return null;

		if (obj instanceof RubyString) {
			return ((RubyString)obj).getUnicodeValue();
		}
		if (obj instanceof RubyHash) {
			Map<Object,Object> hash = new HashMap<Object,Object>();
			for (Object o:((RubyHash)obj).entrySet()) {
				Map.Entry entry = (Map.Entry)o;
				hash.put(convertRubyToJava(entry.getKey()), convertRubyToJava(entry.getValue()));
			}
			return hash;
		}
		if (obj instanceof RubyArray) {
			Collection<Object> arr = new ArrayList<Object>();
			for (Object o:(RubyArray)obj) {
				arr.add(convertRubyToJava(o));
			}
			return arr;
		}
		if (obj instanceof RubySymbol) {
			return ((RubySymbol)obj).asSymbol();
		}
		if (obj instanceof RubyTime) {
			return ((RubyTime)obj).getJavaDate();
		}
		if (obj instanceof RubyNumeric) {
			return JavaUtil.convertRubyToJava((IRubyObject)obj);
		}
		
		// ただのObject
		obj = JavaUtil.convertRubyToJava((IRubyObject)obj);
		
		if (!(obj instanceof IRubyObject)) return obj;
		
		Map<Object,Object> hash = new HashMap<Object,Object>();
		for (Object o:((IRubyObject)obj).getInstanceVariables().entrySet() ) {
			String name = (String)((Map.Entry)o).getKey();
			if (name.startsWith("@")) name = name.replaceFirst("^@", "");
			hash.put(name, convertRubyToJava(((Map.Entry)o).getValue()));
		}
		// ActiveRecord用
		if (((IRubyObject)obj).respondsTo("attributes")) {
			Map attributes = (Map)((IRubyObject)obj).callMethod(((IRubyObject)obj).getRuntime().getCurrentContext(), "attributes");
			for (Object o:attributes.entrySet()) {
				Map.Entry entry = (Map.Entry)o;
				hash.put(entry.getKey().toString(), convertRubyToJava(entry.getValue()) );
			}
		}
		return hash;
	}
	
	public static String getOptionString(RubyHash assocList, String name, String defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return val.asString().getUnicodeValue();
	}

	public static String getOptionString(RubyHash assocList, String name)
	{
		return getOptionString(assocList, name, null);
	}

	public static Integer getOptionInteger(RubyHash assocList, String name, Integer defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return RubyNumeric.fix2int(val);
	}

	public static Integer getOptionInteger(RubyHash assocList, String name)
	{
		return getOptionInteger(assocList, name, null);
	}

	public static Double getOptionDouble(RubyHash assocList, String name, Double defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return RubyNumeric.num2dbl(val);
	}

	public static Double getOptionDouble(RubyHash assocList, String name)
	{
		return getOptionDouble(assocList, name, null);
	}

	public <T> T getOption(RubyHash assocList, String name, T defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return (T)JavaEmbedUtils.rubyToJava(assocList.getRuntime(), val, Object.class);
	}

	public static Date getOptionDate(RubyHash assocList, String name, Date defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return ((RubyTime)val).getJavaDate();
	}
	
	public static IRubyObject getOption(RubyHash assocList, String name)
	{
		Ruby runtime = assocList.getRuntime();
		ThreadContext ctx = runtime.getCurrentContext();
		IRubyObject val = null;
		RubyString keyByString = RubyString.newUnicodeString(runtime, name);
		RubySymbol keyBySymbol = RubySymbol.newSymbol(runtime, name);
		if (assocList.callMethod(ctx, "has_key?", keyByString).isTrue()) {
			val = assocList.callMethod(ctx, "[]", keyByString);
		} else if (assocList.callMethod(ctx, "has_key?", keyBySymbol).isTrue()) {
			val = assocList.callMethod(ctx, "[]", keyBySymbol);
		}
		return val;
	}
}
