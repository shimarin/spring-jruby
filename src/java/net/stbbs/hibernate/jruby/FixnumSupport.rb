class Fixnum
	def to_java_int
		Java::java.lang.Integer.new(self)
	end
end