package net.stbbs.hibernate.jruby;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.jruby.RubyHash;
import org.jruby.RubySymbol;

public class EntitySerializer extends net.stbbs.hibernate.EntitySerializer {
	public Object serialize(Object obj) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
	{
		if (obj instanceof RubyHash) {
			Map<String, Object> map = new HashMap<String, Object>();
			RubyHash rh = (RubyHash)obj;
			for (Object o:rh.entrySet()) {
				Map.Entry entry = (Map.Entry)o;
				Object key = entry.getKey();
				Object value = entry.getValue();
				if (key instanceof RubySymbol) {
					key = ((RubySymbol)key).asSymbol();
				}
				map.put(key.toString(), serialize(value));
			}
			return map;
		}
		// else
		/*
		if (obj instanceof RubyObject) {
	        RubyObject rc = (RubyObject)obj;
	        Map map;
	        if (rc.respondsTo("attributes")) {	// だっくたいぴん
	            // ActiveRecordオブジェクト用
	        	map = (Map)rc.callMethod(rc.getRuntime().getCurrentContext(), "attributes");
	        } else {
	        	map = rc.getInstanceVariables(); 
	        }
		}
		*/
		
		return super.serialize(obj);
	}
}
