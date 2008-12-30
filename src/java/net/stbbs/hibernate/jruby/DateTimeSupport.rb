class Time
	def add(field, amount)
		cal = Java::java.util.Calendar::getInstance
		cal.setTimeInMillis(self.tv_sec * 1000)
		cal.add(field, amount)
		Time.at(cal.getTimeInMillis / 1000)
	end
	
	def add_year(amount)
		add(Java::java.util.Calendar::YEAR, amount)
	end
	
	def add_month(amount)
		add(Java::java.util.Calendar::MONTH, amount)
	end
	
	def add_day(amount)
		add(Java::java.util.Calendar::DAY_OF_YEAR, amount)
	end
	
	def add_hour(amount)
		add(Java::java.util.Calendar::HOUR, amount)
	end
	
	def add_minute(amount)
		add(Java::java.util.Calendar::MINUTE, amount)
	end
	
	def add_second(amount)
		add(Java::java.util.Calendar::SECOND, amount)
	end

	def to_sql_date
		Java::java.sql.Date.new(self.tv_sec * 1000)
	end
	
	def to_sql_timestamp
		Java::java.sql.Timestamp.new(self.tv_sec * 1000)
	end

	def self.from_java(javadate)
		Time.at(javadate.getTime / 1000)
	end
end
