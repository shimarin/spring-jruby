package net.stbbs.jruby.modules;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
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
		Util.registerDecorator(runtime, FontDecorator.class);
		Util.registerDecorator(runtime, BasicStrokeDecorator.class);
	}
	
	@JRubyConstant("BufferedImage") public static final Class BUFFERED_IMAGE = BufferedImage.class;
	@JRubyConstant("Color") public static final Class COLOR = Color.class;
	@JRubyConstant("BasicStroke") public static final Class BASIC_STROKE = BasicStroke.class;
	@JRubyConstant("GeneralPath") public static final Class GENERAL_PATH = GeneralPath.class;
	@JRubyConstant("Font") public static final Class FONT = Font.class;

	@Decorator(BufferedImage.class)
	public static class BufferedImageDecorator {
		private BufferedImage bufferedImage;
		public BufferedImageDecorator(BufferedImage bufferedImage)
		{
			this.bufferedImage = bufferedImage;
		}
		public BufferedImageDecorator()
		{
		}
		
		@JRubyMethod(required=2,optional=2)
		public BufferedImage initialize(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 2) {
				throw runtime.newArgumentError(args.length, 2);
			}
			int width = (int)args[0].convertToInteger().getLongValue();
			int height = (int)args[1].convertToInteger().getLongValue();
			int type = BufferedImage.TYPE_INT_RGB;
			if (args.length > 2) type = (int)args[2].convertToInteger().getLongValue();
			if (args.length > 3) {
				bufferedImage = new BufferedImage(
					width, height,type,
					(IndexColorModel)JavaEmbedUtils.rubyToJava(runtime, args[3], IndexColorModel.class));
			} else {
				bufferedImage = new BufferedImage(width, height,type);
			}

			if (block.isGiven()) {
				final Graphics2D g2d = (Graphics2D)bufferedImage.getGraphics();
				
				IRubyObject g = JavaEmbedUtils.javaToRuby(runtime, g2d);
				block.call(
					runtime.getCurrentContext(), 
					new IRubyObject[] { g});
			}
			return bufferedImage;
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
			if (format.equals("png")) {
			} else if (format.equals("jpeg") || format.equals("jpg")) {
				format = "jpg";
			} else {
				throw runtime.newArgumentError("Image file format '" + format + "' is not supported.");
			}
			return BufferedImageRenderer.toByteArray(bufferedImage, format);
		}

	}
	

	@Decorator(BasicStroke.class)
	public static class BasicStrokeDecorator {
		private BasicStroke stroke;
		public BasicStrokeDecorator(BasicStroke stroke) {
			this.stroke = stroke;
		}
		public BasicStrokeDecorator() {
		}
		@JRubyMethod(required=1,optional=1)
		public BasicStroke initialize(IRubyObject self, IRubyObject[] args, Block block)
		{
			if (args.length < 1) return (stroke = new BasicStroke());
			//デフォルト = 1.0、CAP_SQUARE、JOIN_MITER、マイターリミット 10.0 です。
			RubyHash opts = args[0].convertToHash();
			Double width = Util.getOptionDouble(opts, "width", 1.0);
			Integer cap = Util.getOptionInteger(opts, "cap", BasicStroke.CAP_SQUARE);
			Integer join = Util.getOptionInteger(opts, "join", BasicStroke.JOIN_MITER);
			Double miterlimit = Util.getOptionDouble(opts, "miterlimit", 10.0);
			float[] dash = Util.getOptionFloatArray(opts, "dash");
			Double dash_phase = Util.getOptionDouble(opts, "dash_phase", 0.0);
			if (dash == null && dash_phase == null) {
				return (stroke = new BasicStroke(width.floatValue(), cap, join, miterlimit.floatValue()));
			}
			// else
			return (stroke = new BasicStroke(width.floatValue(), cap, join, miterlimit.floatValue(), dash, dash_phase.floatValue()));
		}
	}
	
	@Decorator(Font.class)
	public static class FontDecorator {
		private Font font;
		
		public FontDecorator(Font font)
		{
			this.font = font;
		}
		
		public FontDecorator()
		{
		}
		
		@JRubyMethod(required=1,optional=1)
		public Font initialize(IRubyObject self, IRubyObject[] args, Block block)
		{
			Ruby runtime = self.getRuntime();
			if (args.length < 1) {
				throw runtime.newArgumentError(args.length, 1);
			}
			int size = (int)args[0].convertToInteger().getLongValue();
			RubyHash opts = args.length > 1? (RubyHash)args[1] : null;
			String name = Util.getOptionString(opts, "name", null);
			int style = Util.getOptionInteger(opts, "style", Font.PLAIN);
			return (font = new Font(name, style, size));
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
		
		@JRubyMethod(name="<<", required=1)
		public IRubyObject set(IRubyObject self, IRubyObject[] args, Block block)
		{
			Object jo = JavaEmbedUtils.rubyToJava(runtime, args[0], null);
			if (jo instanceof Stroke) {
				graphics.setStroke((Stroke)jo);
			} else if (jo instanceof Font) {
				graphics.setFont((Font)jo);
			} else if (jo instanceof Color) {
				graphics.setColor((Color)jo);
			}
			return self;
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
	}
	
	private Ruby runtime;
	
	public GraphicsSupport(Ruby runtime)
	{
		this.runtime = runtime;
	}
	
	@JRubyMethod(optional=2)
	public Object paint(IRubyObject self, IRubyObject[] args, Block block) throws IOException
	{
		IRubyObject width = runtime.newFixnum(512);
		IRubyObject height = runtime.newFixnum(384);
		return self.getMetaClass().getConstant("BufferedImage").callMethod(
				runtime.getCurrentContext(), "new", new IRubyObject[] {width,height}, block);
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
