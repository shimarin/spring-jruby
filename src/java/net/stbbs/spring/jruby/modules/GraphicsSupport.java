package net.stbbs.spring.jruby.modules;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.stbbs.spring.jruby.DownloadContent;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.RubyFloat;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

@Module
public class GraphicsSupport extends AbstractModule {
	@ModuleMethod(arity=ModuleMethod.ARITY_TWO_REQUIRED)
	public IRubyObject newBufferedImage(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) 
	{
		int type = BufferedImage.TYPE_INT_RGB;
		if (args.length > 2) type = RubyNumeric.num2int(args[2]);
		BufferedImage bi = new BufferedImage(
				RubyNumeric.num2int(args[0]),
				RubyNumeric.num2int(args[1]),type);

		if (block.isGiven()) {
			block.call(
				ruby.getCurrentContext(), 
				new IRubyObject[] {JavaEmbedUtils.javaToRuby(self.getRuntime(), bi.getGraphics())});
		}
		return ruby.toRuby(bi);
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public IRubyObject newGeneralPath(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return ruby.toRuby(new GeneralPath());
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject renderJpegForDownload(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write((BufferedImage)ruby.toJava(args[0]), "jpg", baos);
		return ruby.toRuby(new DownloadContent("image/jpeg", baos.toByteArray()));
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject renderPngForDownload(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write((BufferedImage)ruby.toJava(args[0]), "png", baos);
		return ruby.toRuby(new DownloadContent("image/png", baos.toByteArray()));
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public IRubyObject newBasicStroke(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		BasicStroke bs;
		if (args.length > 3) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]),(float) RubyFloat.num2dbl(args[3]));
		else if (args.length > 2) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]));
		else if (args.length > 1) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), BasicStroke.JOIN_ROUND);
		else if (args.length > 0) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]));
		else bs = new BasicStroke();
		return ruby.toRuby(bs);
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_THREE_ARGUMENTS)
	public IRubyObject newFont(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return ruby.toRuby(new Font(args[0].asString().getUnicodeValue(), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2])));
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public IRubyObject newFontBySize(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return ruby.toRuby(new Font(null, Font.PLAIN, RubyNumeric.num2int(args[0])));
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public IRubyObject paint(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		final int type = BufferedImage.TYPE_INT_RGB;
		int width = 512;
		int height = 384;
		if (args.length > 0) width = RubyNumeric.num2int(args[0]);
		if (args.length > 1) height = RubyNumeric.num2int(args[1]);
		BufferedImage bi = new BufferedImage(width, height,type);

		if (block.isGiven()) {
			block.call(
				ruby.getCurrentContext(), 
				new IRubyObject[] {ruby.toRuby(bi.getGraphics())});
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(bi, "png", baos);
		return ruby.toRuby(new DownloadContent("image/png", baos.toByteArray()));
	}

	/**
	 * 定数の登録を行う
	 */
	@Override
	public void onRegister(SpringIntegratedJRubyRuntime ruby, RubyModule module) {
		// こんなやりかたでいいのか？
		module.setConstant("Color", ruby.toRuby(ruby.evalScript("Java::java.awt.Color")));
		module.setConstant("BasicStroke", ruby.toRuby(ruby.evalScript("Java::java.awt.BasicStroke")));
	}

}
