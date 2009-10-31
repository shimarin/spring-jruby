package net.stbbs.jruby.modules;

import javax.imageio.ImageIO;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyConstant;

public class ImageIOSupport {

	@JRubyConstant("ImageIO") public static final Class IMAGE_IO = ImageIO.class;

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		
	}

}
