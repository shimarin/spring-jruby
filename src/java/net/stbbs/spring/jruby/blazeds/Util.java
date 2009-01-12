package net.stbbs.spring.jruby.blazeds;

import java.util.Collection;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyString;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import flex.messaging.io.amf.ASObject;

public class Util {
	
	static public boolean isAppropriateAsSymbol(String str)
	{
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c > 127 || c <= 0x20 || c == ':') return false;
		}
		return true;
	}
	
	static public IRubyObject convertJavaToRuby(Ruby runtime, Object obj)
	{
		if (obj == null) return runtime.getNil();
		if (obj instanceof IRubyObject) return (IRubyObject)obj;
		if (obj instanceof String) return RubyString.newUnicodeString(runtime, (String)obj);
		if (obj instanceof ASObject) {
			RubyHash hash = new RubyHash(runtime);
			ASObject aso = (ASObject)obj;
			for (Object o:aso.entrySet()) {
				Map.Entry entry = (Map.Entry)o;
				String key = (String)entry.getKey();
				if (isAppropriateAsSymbol(key)) {
					hash.put(runtime.newSymbol(key), convertJavaToRuby(runtime, entry.getValue()));
				} else {
					hash.put(key, convertJavaToRuby(runtime, entry.getValue()));
				}
			}
			return hash;
		}
		if (obj instanceof Collection) {
			Collection col = (Collection)obj;
			RubyArray array = runtime.newArray();
			for (Object o:col) {
				array.add(convertJavaToRuby(runtime, o));
			}
			return array;
		}
		if (obj.getClass().isArray()) {
			Object[] arr = (Object[])obj;
			RubyArray array = runtime.newArray();
			for (Object o:arr) {
				array.add(convertJavaToRuby(runtime, o));
			}
			return array;
		}

		return JavaEmbedUtils.javaToRuby(runtime, obj);
	}

}
