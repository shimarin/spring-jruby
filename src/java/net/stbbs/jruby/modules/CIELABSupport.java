package net.stbbs.jruby.modules;

import org.jruby.RubyArray;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class CIELABSupport {
	
	static private final double CO = 16.0F/116.0F;
	
	@JRubyMethod(required=3)
	public RubyArray rgb2cielab(IRubyObject self, IRubyObject[] args, Block block) {
		
		double r = args[0].convertToFloat().getDoubleValue();
		double g = args[1].convertToFloat().getDoubleValue();
		double b = args[2].convertToFloat().getDoubleValue();
		
		r = ((r > 0.04045) ? Math.pow((r+0.055)/1.055,2.4) : r/12.92) * 100;
		g = ((g > 0.04045) ? Math.pow((g+0.055)/1.055,2.4) : g/12.92) * 100;
		b = ((b > 0.04045) ? Math.pow((b+0.055)/1.055,2.4) : b/12.92) * 100;

		// Observer. = 2°, Illuminant = D65
		double x = 0.4124 * r + 0.3576 * g + 0.1805 * b;
		double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		double z = 0.0193 * r + 0.1192 * g + 0.9505 * b;

		x = x/ 95.047;	//ref_X =  95.047   Observer= 2°, Illuminant= D65
		y = y/100.000;	//ref_Y = 100.000
		z = z/108.883;	//ref_Z = 108.883
		x = (x > 0.008856) ? Math.pow(x,1/3.0) : (7.787*x)+(16/116.0);
		y = (y > 0.008856) ? Math.pow(y,1/3.0) : (7.787*y)+(16/116.0);
		z = (z > 0.008856) ? Math.pow(z,1/3.0) : (7.787*z)+(16/116.0);

		RubyArray result = self.getRuntime().newArray(3);
		result.set(0, self.getRuntime().newFloat(116 * y - 16));
		result.set(1, self.getRuntime().newFloat(500 * (x - y)));
		result.set(2, self.getRuntime().newFloat(200 * (y - z)));
		return result;
	}

	private double invfLAB(double v){
		return (v>0.2068927064827586F)? v*v*v: (v-CO)/7.787F;
	}

	@JRubyMethod(required=3)
	public RubyArray cielab2rgb(IRubyObject self, IRubyObject[] args, Block block) {
		double l = args[0].convertToFloat().getDoubleValue();
		double a = args[1].convertToFloat().getDoubleValue();
		double v = args[2].convertToFloat().getDoubleValue();

		double yyn = (l+16.0F)/116.0F;
		double[] xyz = new double[3];
		xyz[0] = invfLAB(a/500.0F+yyn);
		xyz[1] = invfLAB(yyn);
		xyz[2] = invfLAB(yyn-v/200.0F);
		
		double[] rgb = new double[3];
		rgb[0] =  3.2406*xyz[0]-1.5372*xyz[1]-0.4986*xyz[2];
		rgb[1] = -0.9689*xyz[0]+1.8758*xyz[1]+0.0415*xyz[2];
		rgb[2] =  0.0557*xyz[0]-0.2040*xyz[1]+1.0570*xyz[2];
		for (int i = 0; i < 3; i++) {
			if (rgb[i] < 0.0) rgb[i] = 0.0;
			else if (rgb[i] > 1.0) rgb[i] = 1.0;
		}

		RubyArray result = self.getRuntime().newArray(3);
		result.set(0, self.getRuntime().newFloat(rgb[0]));
		result.set(1, self.getRuntime().newFloat(rgb[1]));
		result.set(2, self.getRuntime().newFloat(rgb[2]));
		return result;

	}
}
