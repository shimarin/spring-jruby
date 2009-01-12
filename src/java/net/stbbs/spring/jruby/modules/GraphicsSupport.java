package net.stbbs.spring.jruby.modules;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.stbbs.jruby.Util;
import net.stbbs.spring.jruby.DownloadContent;
import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

@Module
public class GraphicsSupport extends AbstractModule {
	public static IRubyObject enhanceBuffer(final SpringIntegratedJRubyRuntime ruby, final BufferedImage buf)
	{
		IRubyObject rbi = ruby.toRuby(buf);
		rbi.getSingletonClass().defineMethod("getGraphics", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				BufferedImage bi = (BufferedImage)ruby.toJava(self);
				return enhanceGraphics(ruby, (Graphics2D)bi.getGraphics());
			}
			public Arity getArity() { return Arity.NO_ARGUMENTS; }
		});
		rbi.getSingletonClass().defineMethod("download", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				String format = "png";
				if (args.length > 0) {
					format = args[0].asString().getUnicodeValue();
				}
				BufferedImage bi = (BufferedImage)ruby.toJava(self);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					if (format.equals("png")) {
							ImageIO.write(bi, "png", baos);
						return ruby.toRuby(new DownloadContent("image/png", baos.toByteArray()) );
					} else if (format.equals("jpeg") || format.equals("jpg")) {
						ImageIO.write(bi, "jpg", baos);
						return ruby.toRuby(new DownloadContent("image/jpeg", baos.toByteArray()) );
					} else {
						throw ruby.newArgumentError("Image file format '" + format + "' is not supported.");
					}				
				} catch (IOException e) {
					throw ruby.newIOError(e.getMessage());
				}
			}
			public Arity getArity() { return Arity.OPTIONAL; }
		});
		return rbi;
	}
	
	public static IRubyObject enhanceGraphics(final SpringIntegratedJRubyRuntime ruby, final Graphics2D jg)
	{
		IRubyObject g = (IRubyObject)ruby.toRuby(jg);
		g.getSingletonClass().defineMethod("drawGeneralPath", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				GeneralPath gp = new GeneralPath();
				if (block.isGiven()) {
					block.call(
						ruby.getCurrentContext(), 
						new IRubyObject[] {ruby.toRuby(gp)});
					jg.draw(gp);
				}
				return ruby.toRuby(gp);
			}

			public Arity getArity() {
				return Arity.NO_ARGUMENTS;
			}
		});
		g.getSingletonClass().defineMethod("setStroke", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が足りない場合エラー
				if (args.length < 1) {
					throw ruby.newArgumentError("Method requires at least one argument.");
				}
				Object jo = ruby.toJava(args[0]);
				if (jo instanceof Stroke) {
					((Graphics2D)ruby.toJava(self)).setStroke((Stroke)jo);
				} else {
					RubyHash options = (RubyHash)args[0];
					Double width = Util.getOptionDouble(options, "width", 1.0);
					Integer cap = Util.getOptionInteger(options, "cap", BasicStroke.CAP_SQUARE);
					Integer join = Util.getOptionInteger(options, "join", BasicStroke.JOIN_MITER);
					Double miterlimit = Util.getOptionDouble(options, "miterlimit", 10.0);
					BasicStroke bs = new BasicStroke(width.floatValue(), cap, join, miterlimit.floatValue());
					((Graphics2D)ruby.toJava(self)).setStroke(bs);
				}
				return self;
			}
			public Arity getArity() { return Arity.ONE_ARGUMENT; }
		});
		g.getSingletonClass().defineMethod("setFont", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が足りない場合エラー
				if (args.length < 1) {
					throw ruby.newArgumentError("Method requires at least one argument.");
				}
				Object jo = ruby.toJava(args[0]);
				Graphics2D g = (Graphics2D)ruby.toJava(self);
				if (jo instanceof Font) {
					g.setFont((Font)jo);
				} else {
					RubyHash options = (RubyHash)args[0];
					String name = Util.getOptionString(options, "name");
					Integer style = Util.getOptionInteger(options, "style", Font.PLAIN);
					Integer size = Util.getOptionInteger(options, "size", g.getFont().getSize());
					g.setFont(new Font(name, style, size));
				}
				return self;
			}
			public Arity getArity() { return Arity.ONE_ARGUMENT; }
		});
		g.getSingletonClass().defineMethod("setColor", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				// 引数が足りない場合エラー
				if (args.length < 1) {
					throw ruby.newArgumentError("Method requires at least one argument.");
				}
				Object jo = ruby.toJava(args[0]);
				Graphics2D g = (Graphics2D)ruby.toJava(self);
				if (jo instanceof Color) {
					g.setColor((Color)jo);
				} else {
					if (args.length < 3) {
						throw ruby.newArgumentError("Method requires at least three arguments(r,g,b,(a)).");
					}
					float rr = (float)RubyNumeric.num2dbl(args[0]);
					float gg = (float)RubyNumeric.num2dbl(args[1]);
					float bb = (float)RubyNumeric.num2dbl(args[2]);
					Float aa = args.length > 3? (float)RubyNumeric.num2dbl(args[3]) : null;
					if (aa == null) {
						g.setColor(new Color(rr, gg, bb));
					} else {
						g.setColor(new Color(rr, gg, bb, aa));
					}
				}
				return self;
			}
			public Arity getArity() { return Arity.ONE_ARGUMENT; }
		});
		return g;
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_TWO_REQUIRED)
	public Object newBufferedImage(final SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) 
	{
		// 引数が足りない場合エラー
		if (args.length < 2) {
			throw ruby.newArgumentError("Method requires at least two arguments.");
		}

		int type = BufferedImage.TYPE_INT_RGB;
		if (args.length > 2) type = RubyNumeric.num2int(args[2]);
		BufferedImage bi = new BufferedImage(
				RubyNumeric.num2int(args[0]),
				RubyNumeric.num2int(args[1]),type);

		IRubyObject rbi = enhanceBuffer(ruby, bi);
		
		if (block.isGiven()) {
			final Graphics2D g2d = (Graphics2D)bi.getGraphics();
			
			IRubyObject g = enhanceGraphics(ruby, g2d);
			block.call(
				ruby.getCurrentContext(), 
				new IRubyObject[] { g});
		}
		return rbi;
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_NO_ARGUMENTS)
	public Object newGeneralPath(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		return new GeneralPath();
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
		return bi.callMethod(ruby.getCurrentContext(), "download", ruby.newSymbol("png"));
	}

	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public Object generateAnimatedGif(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		AnimatedGifEncoder e = new AnimatedGifEncoder();
		int delay = 1000;
		int repeat = 0;
		if (args.length > 0) delay = RubyNumeric.fix2int(args[0]);
		if (args.length > 1) repeat = RubyNumeric.fix2int(args[1]);
		e.setDelay(delay);
		e.setRepeat(repeat);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		e.start(baos);
		if (block.isGiven()) {
			block.call(ruby.getCurrentContext(), new IRubyObject[] {ruby.toRuby(e)});
		}
		e.finish();
		return new DownloadContent("image/gif", baos.toByteArray()) ;
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public Object newGraphicsFrame(SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block)
	{
		final int type = BufferedImage.TYPE_INT_RGB;
		int width = 512;
		int height = 384;
		if (args.length > 0) width = RubyNumeric.num2int(args[0]);
		if (args.length > 1) height = RubyNumeric.num2int(args[1]);
		return new GraphicsFrame(ruby, width, height);
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
