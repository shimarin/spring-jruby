package net.stbbs.spring.jruby.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleMethod {
	public static final int ARITY_OPTIONAL = -1;
    public final static int ARITY_NO_ARGUMENTS = 0;
    public final static int ARITY_ONE_ARGUMENT = 1;
    public final static int ARITY_TWO_ARGUMENTS = 2;
    public final static int ARITY_THREE_ARGUMENTS = 3;
    public final static int ARITY_ONE_REQUIRED = -2;
    public final static int ARITY_TWO_REQUIRED = -3;
    public final static int ARITY_THREE_REQUIRED = -3;

	String value() default "";
	int arity() default ARITY_OPTIONAL;
}
