package net.stbbs.jruby.modules;

import java.util.Collection;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jruby.Ruby;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

public class MIDISupport {
	static final int TICKS_PER_BEAT = 96;	// 四分音符あたりのティック数
	
	private Object toJava(Ruby runtime, IRubyObject ro)
	{
		return JavaEmbedUtils.rubyToJava(runtime, ro, null);
	}
	
	private IRubyObject toRuby(Ruby runtime, Object jo)
	{
		return JavaEmbedUtils.javaToRuby(runtime, jo);
	}
	
	protected void parseMML(IRubyObject self, String mml) throws InvalidMidiDataException
	{
		Ruby runtime = self.getRuntime();
		Track track = (Track)toJava(runtime, self);
		int channel = RubyNumeric.fix2int(self.getInstanceVariable("@channel"));
		int position = RubyNumeric.fix2int(self.getInstanceVariable("@position"));
		int octave = RubyNumeric.fix2int(self.getInstanceVariable("@octave"));
		int notelen = RubyNumeric.fix2int(self.getInstanceVariable("@notelen"));
		IRubyObject tie = self.getInstanceVariable("@tie");
		int i = 0;
		while (i < mml.length()) {
			char c = mml.charAt(i);
			int noteno;
			switch (Character.toLowerCase(c)) {
			case 'c':
				noteno = (octave + 1) * 12 + 0;
				break;
			case 'd':
				noteno = (octave + 1) * 12 + 2;
				break;
			case 'e':
				noteno = (octave + 1) * 12 + 4;
				break;
			case 'f':
				noteno = (octave + 1) * 12 + 5;
				break;
			case 'g':
				noteno = (octave + 1) * 12 + 7;
				break;
			case 'a':
				noteno = (octave + 1) * 12 + 9;
				break;
			case 'b':
				noteno = (octave + 1) * 12 + 11;
				break;
			case 'r':
				noteno = 0;
				break;
			case 'o':
				int o = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					o*= 10;
					o += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
				}
				octave = o;
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(runtime, octave));
				continue;
			case 'l':
				int l = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					l *= 10;
					l += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
				}
				notelen = TICKS_PER_BEAT * 4 / l;
				if (i < mml.length() && mml.charAt(i) == '.') {
					notelen = notelen * 3 / 2;
					i++;
				}
				self.setInstanceVariable("@notelen", RubyNumeric.int2fix(runtime, notelen));
				continue;
			case 't':
				int t = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					t *= 10;
					t += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
				}
				if (t > 0) {
					// テンポ変更のメッセージを作成・送信
					long val = 60000000 / t;
					byte byte3 = new Long(val).byteValue();
					byte byte2 = new Long(Long.rotateRight(val, 8)).byteValue();
					byte byte1 = new Long(Long.rotateRight(val, 16)).byteValue();
					MetaMessage mm = new MetaMessage();
					mm.setMessage(0x51, new byte[] {byte1, byte2, byte3}, 3);
					track.add(new MidiEvent(mm, position));
				}
				continue;
			case 'v':
				int v = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					v *= 10;
					v += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
				}
				continue;
			case '@':
				int prg = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					prg *= 10;
					prg += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
				}
				ShortMessage sm = new ShortMessage();
				sm.setMessage(ShortMessage.PROGRAM_CHANGE, channel, prg - 1, 0);
				track.add(new MidiEvent(sm, position));
				continue;
			case '>':
				octave++;
				i++;
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(runtime, octave));
				continue;
			case '<':
				octave--;
				i++;
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(runtime, octave));
				continue;
			case ' ':
			case '\t':
				i++;
				continue;
			default:
				throw runtime.newArgumentError("Invalida note char '" + c + "'");
			}
			i++;
			if (i < mml.length() && noteno > 0) {
				if (mml.charAt(i) == '+' || mml.charAt(i) == '#') {
					noteno += 1;
					i ++;
				} else if (mml.charAt(i) == '-') {
					noteno -= 1;
					i ++;
				}
			}
			int len = 0;
			while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
				len *= 10;
				len += Integer.parseInt(Character.toString(mml.charAt(i)));
				i++;
			}
			int ticks = len == 0? notelen : TICKS_PER_BEAT * 4 / len;
			if (i < mml.length() && mml.charAt(i) == '.') { // 付点
				ticks = ticks * 3 / 2;
				i++;
			}
			if (tie != null && !tie.isNil()) {
				int prevnoteno = RubyNumeric.fix2int(tie);
				if (prevnoteno != noteno) {
					ShortMessage sm = new ShortMessage();
					sm.setMessage(ShortMessage.NOTE_OFF | channel, prevnoteno, 100);
					track.add(new MidiEvent(sm, position));
					tie = runtime.getNil();
					self.setInstanceVariable("@tie", tie);	// タイ終了
				}
			}
			if (noteno > 0) {
				if (tie == null || tie.isNil()) {	// タイ中じゃなければノートON
					ShortMessage sm = new ShortMessage();
					sm.setMessage(ShortMessage.NOTE_ON | channel, noteno, 100);
					track.add(new MidiEvent(sm, position));
				} else {
					tie = runtime.getNil();
					self.setInstanceVariable("@tie", tie);	// タイ終了
				}
				
				position += ticks;

				if (i < mml.length() && mml.charAt(i) == '&') { // タイ
					tie = RubyNumeric.int2fix(runtime, noteno);
					i++;
				} else {
					ShortMessage sm = new ShortMessage();
					sm.setMessage(ShortMessage.NOTE_OFF | channel, noteno, 100);
					track.add(new MidiEvent(sm, position));
				}
			} else { // 休符
				position += ticks;
			}
			self.setInstanceVariable("@position", RubyNumeric.int2fix(runtime, position));
		}
		
	}
	
	
	
	@JRubyMethod(optional=1)
	public IRubyObject newSequence(IRubyObject self, IRubyObject[] args, Block block) throws InvalidMidiDataException
	{
		Ruby runtime = self.getRuntime();
		Sequence sq =  new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
		IRubyObject rsq = toRuby(runtime,sq);
		rsq.getSingletonClass().defineMethod("createTrack", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				int channel = 0;
				if (args.length > 0) {
					channel = RubyNumeric.fix2int(args[0]);
					if (channel > 15) throw runtime.newArgumentError("Channel number must be less than 16");
				}
				Sequence seq = (Sequence)toJava(runtime, self);
				IRubyObject rtr = toRuby(runtime, seq.createTrack());
				rtr.setInstanceVariable("@position", RubyNumeric.int2fix(runtime, 0));
				rtr.setInstanceVariable("@octave", RubyNumeric.int2fix(runtime, 4));
				rtr.setInstanceVariable("@notelen", RubyNumeric.int2fix(runtime, TICKS_PER_BEAT));
				rtr.setInstanceVariable("@channel", RubyNumeric.int2fix(runtime, channel));
				rtr.getSingletonClass().defineMethod("<<", new Callback() {
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
						Ruby runtime = self.getRuntime();
						try {
							for (IRubyObject arg:args) {
								if (arg instanceof Collection) {
									for (Object o:(Collection)arg) {
										String notes = o.toString();
										parseMML(self, notes);
									}
								} else {
									String notes = arg.asString().getUnicodeValue();
									parseMML(self, notes);
								}
							}
						} catch (InvalidMidiDataException e) {
							runtime.newArgumentError(e.getMessage());
						}
						return self;
					}
					public Arity getArity() { return Arity.ONE_ARGUMENT; }
				});
				
				if (block.isGiven()) {
					block.call(runtime.getCurrentContext(), new IRubyObject[] {rtr});
				}
				
				return rtr;
			}
			public Arity getArity() { return Arity.NO_ARGUMENTS; }

		});
		rsq.getSingletonClass().defineMethod("playSynchronous", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Ruby runtime = self.getRuntime();
				Sequence seq = (Sequence)toJava(runtime, self);
				try {
					Sequencer sequencer = MidiSystem.getSequencer();
					sequencer.open();
					sequencer.setSequence(seq);
					sequencer.start();
					while (sequencer.isRunning()) Thread.sleep(100);
					sequencer.close();
				} catch (MidiUnavailableException e) {
					runtime.newRuntimeError(e.getMessage());
				} catch (InvalidMidiDataException e) {
					runtime.newRuntimeError(e.getMessage());
				} catch (InterruptedException e) {
					// Interrupted.
				}
				return self;
			}
			public Arity getArity() { return Arity.NO_ARGUMENTS; }
		});		
		if (block.isGiven()) {
			block.call(runtime.getCurrentContext(), new IRubyObject[] {rsq});
		}
		
		return rsq;
	}

	@JRubyMethod(required=1)
	public IRubyObject play(IRubyObject self, IRubyObject[] args, Block block) throws InvalidMidiDataException
	{
		Ruby runtime = self.getRuntime();
		if (args.length < 1) {
			throw runtime.newArgumentError("Method required at least one argument");
		}
		IRubyObject seq = self.callMethod(runtime.getCurrentContext(), "newSequence");
		int channel = 0;
		for (IRubyObject arg:args) {
			IRubyObject track = seq.callMethod(runtime.getCurrentContext(), "createTrack", RubyNumeric.int2fix(runtime, channel));
			if (arg instanceof Collection ) {
				for (Object o:(Collection)arg) {
					String mml = o.toString();
					track.callMethod(runtime.getCurrentContext(), "<<", runtime.newString(mml));
				}
			} else {
				String mml = arg.asString().getUnicodeValue();
				track.callMethod(runtime.getCurrentContext(), "<<", runtime.newString(mml));
			}
			channel++;
		}
		return seq.callMethod(runtime.getCurrentContext(), "playSynchronous");
	}

}
