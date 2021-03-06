package net.stbbs.jruby;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNil;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyConstant;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyModule;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.javasupport.JavaObject;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.util.KCode;

public class Util {
	static Log logger = LogFactory.getLog(Util.class);	
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

	public <T> T getOption(RubyHash hash, String name, T defaultValue)
	{
		if (hash == null) return defaultValue;
		IRubyObject val = getOption(hash, name);
		if (val == null) return defaultValue;
		return (T)JavaEmbedUtils.rubyToJava(hash.getRuntime(), val, Object.class);
	}

	public static Date getOptionDate(RubyHash assocList, String name, Date defaultValue)
	{
		IRubyObject val = getOption(assocList, name);
		if (val == null) return defaultValue;
		return ((RubyTime)val).getJavaDate();
	}
	
	public static float[] getOptionFloatArray(RubyHash hash, String name)
	{
		IRubyObject val = getOption(hash, name);
		if (val == null) return null;
		IRubyObject[] array = Util.convertRubyArray(val.convertToArray());
		float[] result = new float[array.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (float)array[i].convertToFloat().getDoubleValue();
		}
		return result;
	}

	
	public static IRubyObject getOption(RubyHash hash, String name)
	{
		if (hash == null) return null;
		Map<String,IRubyObject> map = Util.convertRubyHash(hash);
		return map.get(name);
	}

	public static Arity createArityFromAnnotation(JRubyMethod anno) {
        if (anno.optional() > 0 || anno.rest()) {
            return Arity.createArity(-(anno.required() + 1));
        }
        return Arity.createArity(anno.required());
    }
	
	static private Object getDecoratorObject(final Ruby runtime, IRubyObject self, final Class decoratorClass)
	{
		synchronized (self) {
			Map<Class,Object> decmap = getClassAndInstanceMap(runtime, self, decoratorClass, "@_java_decolator_objects"); 
			Object decobj = decmap.get(decoratorClass);
			if (decobj != null) return decobj;
			
			Object target = JavaEmbedUtils.rubyToJava(runtime, self, null);
			decobj = instantiateObjectUsingAppropriateConstructor(runtime, decoratorClass, target);
			
			decmap.put(decoratorClass, decobj);
			return decobj;
		}	
	}
	
	private static final char PACKAGE_SEPARATOR = '.';
	private static final char INNER_CLASS_SEPARATOR = '$';
	private static final String CGLIB_CLASS_SEPARATOR = "$$";
	
	private static String getShortName(String className) {
		int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
		int nameEndIndex = className.indexOf(CGLIB_CLASS_SEPARATOR);
		if (nameEndIndex == -1) {
			nameEndIndex = className.length();
		}
		String shortName = className.substring(lastDotIndex + 1, nameEndIndex);
		shortName = shortName.replace(INNER_CLASS_SEPARATOR, PACKAGE_SEPARATOR);
		return shortName;
	}

	static public RubyModule registerModule(final Ruby runtime, final Class moduleClass)
	{
		String moduleName = getShortName(moduleClass.getName());
		JRubyModule rm = (JRubyModule) moduleClass.getAnnotation(JRubyModule.class);
		if (rm != null && rm.name().length > 0) {
			moduleName = rm.name()[0];
		}
		RubyModule newModule = runtime.defineModule(moduleName);
		
		Method[] methods = moduleClass.getMethods();
		for (final Method m:methods) {
			JRubyMethod mm = m.getAnnotation(JRubyMethod.class);
			if (mm == null) continue;
			
			String methodName = m.getName();
			if (mm.name().length > 0) methodName = mm.name()[0]; 
			final Arity arity = createArityFromAnnotation(mm);
			registerModuleMethod(newModule, moduleClass, m, methodName, arity);
			
		}
		Field[] fields = moduleClass.getFields();
		for (Field f:fields) {
			if ((f.getModifiers() & Modifier.STATIC) == 0 || (f.getModifiers() & Modifier.PUBLIC) == 0) continue;
			JRubyConstant c = f.getAnnotation(JRubyConstant.class);
			if (c == null) continue;
			String name = f.getName();
			if (c.value().length > 0) name = c.value()[0];
			Object value;
			try {
				value = f.get(null);
			} catch (IllegalArgumentException e) {
				throw RaiseException.createNativeRaiseException(runtime, e);
			} catch (IllegalAccessException e) {
				throw RaiseException.createNativeRaiseException(runtime, e);
			}
			if (value instanceof Class) {
				// Javaクラスの場合は特別な措置を施す
				value = getJavaClassProxy(runtime, (Class)value);
			}
			newModule.defineConstant(name, JavaEmbedUtils.javaToRuby(runtime, value));
		}
		try {
			Method m = moduleClass.getMethod("onRegister", RubyModule.class);
			if ((m.getModifiers() & Modifier.STATIC) != 0 && (m.getModifiers() & Modifier.PUBLIC) != 0) {
				m.invoke(null, newModule);
			}			
		} catch (SecurityException e) {
			runtime.newSecurityError(e.getMessage());
		} catch (NoSuchMethodException e) {
			// do nothing
		} catch (IllegalArgumentException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (IllegalAccessException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (InvocationTargetException e) {
			throw RaiseException.createNativeRaiseException(runtime, e.getTargetException());
		}
		return newModule;
	}
	
	static public RubyModule[] registerDecorator(final Ruby runtime, final Class decoratorClass)
	{
		Decorator d = (Decorator)decoratorClass.getAnnotation(Decorator.class);
		if (d == null) return null;
		RubyModule[] modules = new RubyModule[d.value().length];
		int i = 0;
		for (Class target:d.value()) {
			modules[i++] = registerDecorator(runtime, target.getName(), decoratorClass);
		}
		return modules;
	}
	
	static public Object invokeJavaImplementedRubyMethod(
			Method method, Object instance, IRubyObject self, IRubyObject[] args , Block block)
	{
		Ruby runtime = self.getRuntime();
		try {
			return method.invoke(instance, self, args, block);
		} catch (IllegalArgumentException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (IllegalAccessException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (InvocationTargetException e) {
			Throwable te = e.getTargetException();
			if (te instanceof RuntimeException) throw (RuntimeException)te;
			if (te instanceof IOException) throw runtime.newIOErrorFromException((IOException)te);
			throw RaiseException.createNativeRaiseException(runtime, te);
		}
		
	}
	
	static public RubyModule registerDecorator(final Ruby runtime, RubyModule module, final Class decoratorClass)
	{
		Method[] methods = decoratorClass.getMethods();
		for (final Method m:methods) {
			JRubyMethod mm = m.getAnnotation(JRubyMethod.class);
			if (mm == null) continue;

			String methodName = m.getName();
			if (mm.name().length > 0) methodName = mm.name()[0]; 
			final Arity arity = createArityFromAnnotation(mm);
			if (methodName.equals("initialize")) {
				module.defineMethod(methodName, new Callback(){
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
						Object instance;
						try {
							instance = decoratorClass.newInstance();
						} catch (InstantiationException e) {
							throw RaiseException.createNativeRaiseException(runtime, e);
						} catch (IllegalAccessException e) {
							throw RaiseException.createNativeRaiseException(runtime, e);
						}
						Object rst = invokeJavaImplementedRubyMethod(m, instance, self, args, block);
						if (rst == null) {
							throw runtime.newRuntimeError("initialize() returned null object");
						}
						self.callMethod(runtime.getCurrentContext(), "java_object=", new IRubyObject[] { JavaObject.wrap(runtime, rst)});
						return self;
					}
	
					public Arity getArity() {
						return arity;
					}
				});
			} else {
				module.defineMethod(methodName, new Callback(){
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
						Object instance = getDecoratorObject(runtime, self, decoratorClass);
						Object rst = invokeJavaImplementedRubyMethod(m, instance, self, args, block);
						if (rst instanceof IRubyObject) return (IRubyObject)rst;
						if (rst instanceof String) return RubyString.newUnicodeString(runtime, (String)rst);
						return JavaEmbedUtils.javaToRuby(runtime, rst);
					}
	
					public Arity getArity() {
						return arity;
					}
				});
			}
		}
		try {
			Method m = decoratorClass.getMethod("onRegister", RubyModule.class);
			if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()) ){
				m.invoke(null, module);
			}			
		} catch (SecurityException e) {
			runtime.newSecurityError(e.getMessage());
		} catch (NoSuchMethodException e) {
			// do nothing
		} catch (IllegalArgumentException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (IllegalAccessException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (InvocationTargetException e) {
			throw RaiseException.createNativeRaiseException(runtime, e.getTargetException());
		}
		return module;
	}
	
	static public RubyModule registerDecorator(final Ruby runtime, String javaClassName, final Class decoratorClass)
	{
		RubyModule module = registerDecorator(runtime, getJavaClassProxy(runtime, javaClassName), decoratorClass);
		// Stringには特別な扱いをする
		if (javaClassName.equals("java.lang.String")) {
			registerDecorator(runtime, runtime.getString(), decoratorClass);
		}
		return module;
	}

	static public void registerDecorator(final Ruby runtime, Class targetClass, final Class decoratorClass)
	{
		registerDecorator(runtime, targetClass.getName(), decoratorClass);
	}

	/**
	 * Rubyの {:hoge=>1,:honya=>2} みたいのを Javaの Map<String,IRubyObject>に変換する
	 * @param hash
	 * @return
	 */
	static public Map<String,IRubyObject> convertRubyHash(RubyHash hash)
	{
		if (hash == null) return null;
		Ruby runtime = hash.getRuntime();
		Map<String,IRubyObject> result = new HashMap<String,IRubyObject>();
		RubyArray keys = hash.keys();
		for (int i = 0; i < keys.getLength(); i++) {
			IRubyObject key = keys.at(RubyNumeric.int2fix(runtime, i));
			IRubyObject value = hash.fastARef(key);
			result.put(key.asString().getUnicodeValue(), value);
		}
		return result;
	}
	
	static public void setValueToBean(Object obj, RubyHash hash)
	{
		Ruby runtime = hash.getRuntime();
		IRubyObject target = JavaEmbedUtils.javaToRuby(runtime, obj);
		for (Map.Entry<String, IRubyObject> entry:Util.convertRubyHash(hash).entrySet() ) {
			target.callMethod(runtime.getCurrentContext(), entry.getKey() + "=", entry.getValue());
		}
	}
	
	static public void defineJavaModuleFunction(Ruby ruby)
	{
		ruby.getKernel().defineModuleFunction("java_module", new Callback(){

			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				if (args.length > 1) {
					// もし第2引数が与えられているのであれば、それは必須クラス指定
					IRubyObject[] depOn;
					if (args[1] instanceof RubyArray) {
						depOn = convertRubyArray(args[1].convertToArray());
					} else {
						depOn = new IRubyObject[] { args[1] };
					}
					for (int i = 0; i < depOn.length; i++) {
						IRubyObject ro = depOn[i];
						String className = ro.asString().getUnicodeValue();
						try {
							Class.forName(className);
						}
						catch (ClassNotFoundException ex) {
							logger.info("Module " + args[0].toString() + " was not loaded as necessary class " + className + " not exists.");
							return runtime.getNil();	// 必要なクラスのひとつが見つからなかった
						}
					}
				}
				Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
				Class clazz;
				if (jo instanceof Class) {
					clazz = (Class)jo;
				} else {
					try {
						clazz = Class.forName(args[0].asString().getUnicodeValue());
					} catch (ClassNotFoundException e) {
						throw RaiseException.createNativeRaiseException(runtime, e);
					}
				}
				return registerModule(runtime, clazz);
			}

			public Arity getArity() {
				return Arity.ONE_REQUIRED;
			}
			
		});
	}

	/**
	 * RubyArrayを Javaの IRubyObject[]に変換する
	 * @param hash
	 * @return
	 */
	static public IRubyObject[] convertRubyArray(RubyArray array)
	{
		if (array == null) return null;
		IRubyObject[] jarray = new IRubyObject[array.getLength()];
		for (int i = 0; i < jarray.length; i++) {
			jarray[i] = array.at(array.getRuntime().newFixnum(i));
		}
		return jarray;
	}

	static public void registerModuleMethod(RubyModule newModule, final Class moduleClass, 
			final Method m, String methodName, final Arity arity)
	{
		final Ruby runtime = newModule.getRuntime();
		final boolean isStatic = ((m.getModifiers() & Modifier.STATIC) != 0);
		Callback callback = new Callback(){
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				try {
					Object instance = getModuleObject(runtime, self, moduleClass);
					Object rst;
					if (isStatic) {
						rst = m.invoke(null, self, args, block);	// staticなら thisなし
					} else {
						rst = m.invoke(instance, self, args, block);
					}
					if (rst instanceof IRubyObject) return (IRubyObject)rst;
					if (rst instanceof String) return RubyString.newUnicodeString(runtime, (String)rst);
					return JavaEmbedUtils.javaToRuby(runtime, rst);
				} catch (IllegalArgumentException e) {
					throw RaiseException.createNativeRaiseException(runtime, e);
				} catch (IllegalAccessException e) {
					throw RaiseException.createNativeRaiseException(runtime, e);
				} catch (InvocationTargetException e) {
					Throwable te = e.getTargetException();
					if (te instanceof RuntimeException) throw (RuntimeException)te;
					if (te instanceof IOException) throw runtime.newIOErrorFromException((IOException)te);
					throw RaiseException.createNativeRaiseException(runtime, te);
				}
			}

			public Arity getArity() {
				return arity;
			}

		};
		if (isStatic) {
			newModule.defineModuleFunction(methodName,callback);
		} else {
			newModule.defineMethod(methodName,callback);
		}
	}

	static private Object getModuleObject(final Ruby runtime, IRubyObject self, final Class moduleClass)
	{
		synchronized (self) {
			Map<Class,Object> modmap = getClassAndInstanceMap(runtime, self, moduleClass, "@_java_module_objects"); 
			Object modobj = modmap.get(moduleClass);
			if (modobj == null) {
				modobj = instantiateObjectUsingAppropriateConstructor(runtime, moduleClass, self);
				modmap.put(moduleClass, modobj);
			}
			return modobj;
		}
	}
	
	static protected Object instantiateObjectUsingAppropriateConstructor(Ruby runtime, Class clazz, Object target)
	{
		Class targetClass = target.getClass();
		// インスタンス化に使用するコンストラクタの候補を列挙する
		Constructor[] ctors = clazz.getConstructors();
		Constructor[] candidates = new Constructor[5];
		for (Constructor ctor:ctors) {
			if ((ctor.getModifiers() & Modifier.PUBLIC) == 0) continue;
			Class[] types = ctor.getParameterTypes();
			if (types.length == 0) candidates[4] = ctor;
			else if (types.length == 2) {
				if (types[0].isAssignableFrom(Ruby.class) && types[1].isAssignableFrom(targetClass)) {
					candidates[0] = ctor;
				} else if (types[0].isAssignableFrom(targetClass) && types[1].isAssignableFrom(Ruby.class)) {
					candidates[1] = ctor;
				}
			} else if (types.length == 1) {
				if (types[0].isAssignableFrom(targetClass)) candidates[2] = ctor;
				else if (types[0].isAssignableFrom(Ruby.class)) candidates[3] = ctor;
			}
		}
		
		Object instance;
		try {
			if (candidates[0] != null) {
				instance = candidates[0].newInstance(runtime, target);
			} else if (candidates[1] != null) {
				instance = candidates[1].newInstance(target, runtime);
			} else if (candidates[2] != null) {
				instance = candidates[2].newInstance(target);
			} else if (candidates[3] != null) {
				instance = candidates[3].newInstance(runtime);
			} else if (candidates[4] != null) {
				instance = candidates[4].newInstance();
			} else {
				throw runtime.newNotImplementedError("No any suitable constructor is defined on " + clazz.getName());
			}
		} catch (InstantiationException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (IllegalAccessException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (SecurityException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (IllegalArgumentException e) {
			throw RaiseException.createNativeRaiseException(runtime, e);
		} catch (InvocationTargetException e) {
			throw RaiseException.createNativeRaiseException(runtime, e.getTargetException());
		}
		return instance;
	}

	static protected Map<Class,Object> getClassAndInstanceMap(final Ruby runtime, IRubyObject self, final Class clazz, String instanceVaariableName)
	{
		IRubyObject rmap = self.getInstanceVariable(instanceVaariableName);
		Map<Class,Object> jmap = rmap != null? (Map<Class, Object>) JavaEmbedUtils.rubyToJava(runtime, rmap, Map.class) : null; 
		if (jmap == null) {
			jmap = new HashMap<Class,Object>();
			self.setInstanceVariable(instanceVaariableName, JavaEmbedUtils.javaToRuby(runtime, jmap));
		}
		return jmap;
	}

	static public RubyModule getJavaClassProxy(final Ruby runtime, String javaClassName)
	{
		RubyModule ju = runtime.getModule("JavaUtilities");
		return (RubyModule)ju.callMethod(runtime.getCurrentContext(), 
				"get_proxy_class", runtime.newString(javaClassName));
	}
	
	static public RubyModule getJavaClassProxy(final Ruby runtime, Class clazz)
	{
		return getJavaClassProxy(runtime, clazz.getName());
	}

	/**
	 * ランタイムの初期化を行う
	 * @return
	 */
	static public Ruby initialize()
	{
		Ruby ruby = JavaEmbedUtils.initialize(new ArrayList());
		ruby.setKCode(KCode.UTF8);

		defineJavaModuleFunction(ruby);

		return ruby;
	}
}
