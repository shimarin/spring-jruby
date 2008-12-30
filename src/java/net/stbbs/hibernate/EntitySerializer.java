package net.stbbs.hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

public class EntitySerializer {
	protected Collection<Object[]> objectMap = new ArrayList<Object[]>();

	protected Object getExisting(Object obj)
	{
		for (Object[] o:objectMap) {
			if (o[0] == obj) {
				return o[1];
			}
		}
		return null;
	}
	
	protected void setToMap(Object orig, Object serialized)
	{
		if (getExisting(orig) != null) return;
		objectMap.add(new Object[] {orig, serialized});
	}

	protected Collection<Object> newCollection(Object forThis)
	{
		Collection<Object> col = new ArrayList<Object>();
		setToMap(forThis, col);
		return col;
	}
	
	protected Object[] newArray(Object forThis, int size)
	{
		Object[] arr = new Object[size];
		setToMap(forThis, arr);
		return arr;
	}
	
	protected Map<String,Object> newMap(Object forThis)
	{
		Map<String,Object> map = new HashMap<String,Object>();
		setToMap(forThis, map);
		return map;
	}
	
	protected Object newObject(Object forThis) throws InstantiationException, IllegalAccessException
	{
		Object obj = forThis.getClass().newInstance();
		setToMap(forThis, obj);
		return obj;
	}
	
	public Object serialize(Object o) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
	{
		if (o == null) return null;
		
		if (o instanceof HibernateProxy) {
			LazyInitializer li = ( (HibernateProxy) o ).getHibernateLazyInitializer();
			if (li.isUninitialized()) {
				return null;
			}
			if ( li.isUnwrap() ) {
				o = li.getImplementation();
			}
		}
		
		if (o instanceof PersistentCollection) {
			PersistentCollection pc = (PersistentCollection)o;
			if (!pc.wasInitialized()) {
				return null;
			}
		}

		if (o instanceof String) return o;
		if (o instanceof Number) return o;
		if (o instanceof java.util.Date) return o;
		if (o instanceof java.util.Calendar) return o;
		if (o instanceof Boolean) return o;

		Object existingOne = getExisting(o);
		if (existingOne != null) return existingOne;

		if (o instanceof Collection) {
			Collection<Object> col = newCollection(o);
			for (Object co:(Collection)o) {
				col.add(serialize(co));
			}
			return col;
		}
		
		if (o.getClass().isArray()) {
			Object[] src = (Object[])o;
			Object[] dst = newArray(o, src.length);
			for (int i = 0; i < dst.length; i++) {
				dst[i] = serialize(src[i]);
			}
			return dst;
		}
		
		//Map<String,Object> map = newMap(o);
		Object clone = newObject(o);
		
		Field[] fields = o.getClass().getFields();
		for (Field f:fields) {
			int mod = f.getModifiers();
			if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
				f.set(clone, serialize(f.get(o)) );
			}
		}
		
		Map desc = PropertyUtils.describe(o);
		if (desc != null) {
			for (Object descEntry:desc.entrySet()) {
				Map.Entry entry = (Map.Entry)descEntry;
				String propName = (String)entry.getKey();
				Object value = PropertyUtils.getSimpleProperty(o, propName);
				if ("class".equals(propName) && value instanceof Class) continue;
				PropertyUtils.setSimpleProperty(clone, propName, serialize(value) );
			}
		}
		return clone;
	}

}
