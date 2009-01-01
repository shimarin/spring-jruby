package net.stbbs.spring.jruby.modules;

import org.jruby.RubyModule;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

public abstract class AbstractModule {
	public abstract void onRegister(SpringIntegratedJRubyRuntime ruby, RubyModule module);
}
