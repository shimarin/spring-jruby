package net.stbbs.jruby.modules;

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
import javax.swing.JFrame;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

import org.jruby.Ruby;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyConstant;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class GraphicsSupport {

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, BufferedImageDecorator.class);
		Util.registerDecorator(runtime, Graphics2DDecorator.class);
	}

	@Decorator(BufferedImage.class)
	public static class BufferedImageDecorator {
		private BufferedImage bufferedImage;
		public BufferedImageDecorator(BufferedImage bufferedImage)
		{
			this.bufferedImage = bufferedImage;
		}
		
		@JRubyMethod
		public Graphics getGraphics(IRubyObject self, IRubyObject[] args, Block block)
		{
			return bufferedImage.getGraphics();
		}
		@JRubyMethod
		public byte[] toByteArray(IRubyObject self, IRubyObject[] args, Block block) throws IOException
		{
			Ruby runtime = self.getRuntime();
			String format = "png";
			if (args.length > 0) {
				format = args[0].asString().getUnicodeValue();
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			if (format.equals("png")) {
					ImageIO.write(bufferedImage, "png", baos);
			} else if (format.equals("jpeg") || format.equals("jpg")) {
				ImageIO.write(bufferedImage, "jpg", baos);
			} else {
				throw runtime.newArgumentError("Image file format '" + format + "' is not supported.");
			}
			return baos.toByteArray();
		}
	}
	
	@Decorator(Graphics2D.class)
	public static class Graphics2DDecorator {
		private Ruby runtime;
		private Graphics2D graphics;
		public Graphics2DDecorator(Ruby runtime, Graphics2D graphics)
		{
			this.runtime = runtime;
			this.graphics = graphics;
		}
		
		@JRubyMethod
		public GeneralPath drawGeneralPath(IRubyObject self, IRubyObject[] args, Block block)
		{
			GeneralPath gp = new GeneralPath();
			if (block.isGiven()) {
				block.call(
					runtime.getCurrentContext(), 
					new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, gp)});
				graphics.draw(gp);
			}
			return gp;
		}
		
		@JRubyMethod
		public IRubyObject setStroke(IRubyObject self, IRubyObject[] args, Block block)
		{
			// 引数が足りない場合エラー
			if (args.length < 1) {
				throw runtime.newArgumentError("Method requires at least one argument.");
			}
			Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
			if (jo instanceof Stroke) {
				graphics.setStroke((Stroke)jo);
			} else {
				RubyHash options = (RubyHash)args[0];
				Double width = Util.getOptionDouble(options, "width", 1.0);
				Integer cap = Util.getOptionInteger(options, "cap", BasicStroke.CAP_SQUARE);
				Integer join = Util.getOptionInteger(options, "join", BasicStroke.JOIN_MITER);
				Double miterlimit = Util.getOptionDouble(options, "miterlimit", 10.0);
				BasicStroke bs = new BasicStroke(width.floatValue(), cap, join, miterlimit.floatValue());
				graphics.setStroke(bs);
			}
			return self;
		}
		@JRubyMethod
		public IRubyObject setFont(IRubyObject self, IRubyObject[] args, Block block) 
		{
			// 引数が足りない場合エラー
			if (args.length < 1) {
				throw runtime.newArgumentError("Method requires at least one argument.");
			}
			Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
			if (jo instanceof Font) {
				graphics.setFont((Font)jo);
			} else {
				RubyHash options = (RubyHash)args[0];
				String name = Util.getOptionString(options, "name");
				Integer style = Util.getOptionInteger(options, "style", Font.PLAIN);
				Integer size = Util.getOptionInteger(options, "size", graphics.getFont().getSize());
				graphics.setFont(new Font(name, style, size));
			}
			return self;
		}
		
		@JRubyMethod
		public IRubyObject setColor(IRubyObject self, IRubyObject[] args, Block block) 
		{
			// 引数が足りない場合エラー
			if (args.length < 1) {
				throw runtime.newArgumentError("Method requires at least one argument.");
			}
			Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
			if (jo instanceof Color) {
				graphics.setColor((Color)jo);
			} else {
				if (args.length < 3) {
					throw runtime.newArgumentError("Method requires at least three arguments(r,g,b,(a)).");
				}
				float rr = (float)RubyNumeric.num2dbl(args[0]);
				float gg = (float)RubyNumeric.num2dbl(args[1]);
				float bb = (float)RubyNumeric.num2dbl(args[2]);
				Float aa = args.length > 3? (float)RubyNumeric.num2dbl(args[3]) : null;
				if (aa == null) {
					graphics.setColor(new Color(rr, gg, bb));
				} else {
					graphics.setColor(new Color(rr, gg, bb, aa));
				}
			}
			return self;
		}
	}
	
	private Ruby runtime;
	
	public GraphicsSupport(Ruby runtime)
	{
		this.runtime = runtime;
	}
	
	@JRubyMethod(required=2)
	static public BufferedImage newBufferedImage(IRubyObject self, IRubyObject[] args, Block block) 
	{
		Ruby runtime = self.getRuntime();
		// 引数が足りない場合エラー
		if (args.length < 2) {
			throw runtime.newArgumentError("Method requires at least two arguments.");
		}

		int type = BufferedImage.TYPE_INT_RGB;
		if (args.length > 2) type = RubyNumeric.num2int(args[2]);
		BufferedImage bi = new BufferedImage(
				RubyNumeric.num2int(args[0]),
				RubyNumeric.num2int(args[1]),type);

		if (block.isGiven()) {
			final Graphics2D g2d = (Graphics2D)bi.getGraphics();
			
			IRubyObject g = JavaEmbedUtils.javaToRuby(runtime, g2d);
			block.call(
				runtime.getCurrentContext(), 
				new IRubyObject[] { g});
		}
		return bi;
	}
	
	@JRubyMethod
	public GeneralPath newGeneralPath(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new GeneralPath();
	}

	@JRubyMethod(optional=3)
	public Object newBasicStroke(IRubyObject self, IRubyObject[] args, Block block)
	{
		BasicStroke bs;
		if (args.length > 3) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]),(float) RubyFloat.num2dbl(args[3]));
		else if (args.length > 2) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]));
		else if (args.length > 1) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]), RubyNumeric.num2int(args[1]), BasicStroke.JOIN_ROUND);
		else if (args.length > 0) bs = new BasicStroke((float)RubyFloat.num2dbl(args[0]));
		else bs = new BasicStroke();
		return bs;
	}
	
	@JRubyMethod(required=3)
	public Object newFont(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new Font(args[0].asString().getUnicodeValue(), RubyNumeric.num2int(args[1]), RubyNumeric.num2int(args[2]));
	}

	@JRubyMethod(required=1)
	public Object newFontBySize(IRubyObject self, IRubyObject[] args, Block block)
	{
		return new Font(null, Font.PLAIN, RubyNumeric.num2int(args[0]));
	}
	
	@JRubyMethod(optional=2)
	public Object paint(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		final int type = BufferedImage.TYPE_INT_RGB;
		int width = 512;
		int height = 384;
		if (args.length > 0) width = RubyNumeric.num2int(args[0]);
		if (args.length > 1) height = RubyNumeric.num2int(args[1]);
		
		IRubyObject bi = self.callMethod(runtime.getCurrentContext(), 
				"newBufferedImage", 
				new IRubyObject[]{
					RubyNumeric.int2fix(runtime, width),
					RubyNumeric.int2fix(runtime, height),
					RubyNumeric.int2fix(runtime, type)},
				block);
		return bi.callMethod(runtime.getCurrentContext(), "download", runtime.newSymbol("png"));
	}

	@JRubyMethod(optional=2)
	public byte[] generateAnimatedGif(IRubyObject self, IRubyObject[] args, Block block) throws IOException
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
			block.call(runtime.getCurrentContext(), new IRubyObject[] {JavaEmbedUtils.javaToRuby(runtime, e)});
		}
		e.finish();
		return baos.toByteArray();
	}
	
	@JRubyMethod(optional=2)
	public GraphicsFrame newGraphicsFrame(IRubyObject self, IRubyObject[] args, Block block)
	{
		final int type = BufferedImage.TYPE_INT_RGB;
		int width = 512;
		int height = 384;
		if (args.length > 0) width = RubyNumeric.num2int(args[0]);
		if (args.length > 1) height = RubyNumeric.num2int(args[1]);
		return new GraphicsFrame(width, height);
	}
	
	static public class GraphicsFrame extends JFrame {

		private BufferedImage buffer;
		
		protected GraphicsFrame(int width, int height)
		{
			buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			this.setSize(width, height);
			this.setVisible(true);
		}
		
		public BufferedImage getBuffer() { return buffer; }
		
		public void paint(Graphics g)
		{
			g.drawImage(buffer, 0, 0, null);
		}
		
	}

}
