package net.stbbs.jruby.modules;

import org.jruby.anno.JRubyConstant;

import net.arnx.jsonic.JSON;

public class JSONSupport {
	@JRubyConstant("JSON") public static Class _JSON = JSON.class;
}
