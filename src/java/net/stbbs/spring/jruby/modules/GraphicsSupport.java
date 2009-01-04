package net.stbbs.spring.jruby.modules;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
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
import org.jruby.RubyObject;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

@Module
public class GraphicsSupport extends AbstractModule {
	@ModuleMethod(arity=ModuleMethod.ARITY_TWO_REQUIRED)
	public Object newBufferedImage(final SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) 
	{
		int type = BufferedImage.TYPE_INT_RGB;
		if (args.length > 2) type = RubyNumeric.num2int(args[2]);
		BufferedImage bi = new BufferedImage(
				RubyNumeric.num2int(args[0]),
				RubyNumeric.num2int(args[1]),type);

		if (block.isGiven()) {
			final Graphics2D g2d = (Graphics2D)bi.getGraphics();
			RubyObject g = (RubyObject)ruby.toRuby(g2d);
			g.getSingletonClass().defineMethod("drawGeneralPath", new Callback() {
				public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
					GeneralPath gp = new GeneralPath();
					if (block.isGiven()) {
						block.call(
							ruby.getCurrentContext(), 
							new IRubyObject[] {ruby.toRuby(gp)});
						g2d.draw(gp);
					}
					return ruby.toRuby(gp);
				}

				public Arity getArity() {
					return Arity.NO_ARGUMENTS;
				}
				
			});
			block.call(
				ruby.getCurrentContext(), 
				new IRubyObject[] { g});
		}
		return bi;
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public Object newGeneralPath(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return new GeneralPath();
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public Object renderJpegForDownload(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write((BufferedImage)ruby.toJava(args[0]), "jpg", baos);
		return new DownloadContent("image/jpeg", baos.toByteArray());
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public Object renderPngForDownload(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write((BufferedImage)ruby.toJava(args[0]), "png", baos);
		return new DownloadContent("image/png", baos.toByteArray());
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public Object newBasicStroke(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		BasicStroke bs;
		if (args.length > 3) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]),(float) RubyFloat.num2dbl(args[3]));
		else if (args.length > 2) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]));
		else if (args.length > 1) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), BasicStroke.JOIN_ROUND);
		else if (args.length > 0) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]));
		else bs = new BasicStroke();
		return bs;
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_THREE_ARGUMENTS)
	public Object newFont(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return new Font(args[0].asString().getUnicodeValue(), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]));
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_ONE_ARGUMENT)
	public Object newFontBySize(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return new Font(null, Font.PLAIN, RubyNumeric.num2int(args[0]));
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public Object paint(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		final int type = BufferedImage.TYPE_INT_RGB;
		int width = 512;
		int height = 384;
		if (args.length > 0) width = RubyNumeric.num2int(args[0]);
		if (args.length > 1) height = RubyNumeric.num2int(args[1]);
		
		IRubyObject bi = self.callMethod(ruby.getCurrentContext(), 
				"newBufferedImage", 
				new IRubyObject[]{
					RubyNumeric.int2fix(ruby.getRuntime(), width),
					RubyNumeric.int2fix(ruby.getRuntime(), height),
					RubyNumeric.int2fix(ruby.getRuntime(), type)},
				block);
		return self.callMethod(ruby.getCurrentContext(), "renderPngForDownload", bi);
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
