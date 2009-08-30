package net.stbbs.jruby.modules;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

import net.stbbs.jruby.Decorator;
import net.stbbs.jruby.Util;

public class DateSupport {

	public static void onRegister(RubyModule module)
	{
		Ruby runtime = module.getRuntime();
		Util.registerDecorator(runtime, DateDecorator.class);
	}
	
	@JRubyMethod
	public static Date newDate(IRubyObject self, IRubyObject[] args, Block block) throws ParseException
	{
		if (args.length == 0) {
			return new Date();
		}
		if (args.length == 1) {
			return DateFormat.getDateTimeInstance().parse(args[0].asString().getUnicodeValue());
		}
		// 引数が足りない場合エラー
		if (args.length < 3) {
			throw self.getRuntime().newArgumentError("Method requires at least 3 argument.");
		}

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, (int)args[0].convertToInteger().getLongValue());
	    cal.set(Calendar.MONTH, (int)args[1].convertToInteger().getLongValue() - 1);
	    cal.set(Calendar.DAY_OF_MONTH, (int)args[2].convertToInteger().getLongValue());
    	cal.set(Calendar.HOUR_OF_DAY, args.length > 3? (int)args[3].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.MINUTE, args.length > 4? (int)args[4].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.SECOND, args.length > 5? (int)args[5].convertToInteger().getLongValue() : 0);
    	cal.set(Calendar.MILLISECOND, args.length > 6? (int)args[6].convertToInteger().getLongValue() : 0);

		return new Date(cal.getTimeInMillis());
	}
	
	@Decorator(Date.class)
	static public class DateDecorator {
		private Date self;
		
		public DateDecorator(Date self)
		{
			this.self = self;
		}
		
		@JRubyMethod
		public String inspect(IRubyObject self, IRubyObject[] args, Block block)
		{
			String userLanguage = System.getProperty("user.langugage");
			if ("ja".equals(userLanguage)) {
				DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS"); // TODO: make it mt safe
				return df.format(this.self);
			}
			// else
			return this.self.toString();
		}
	}
}
