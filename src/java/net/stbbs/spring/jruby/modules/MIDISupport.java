package net.stbbs.spring.jruby.modules;

import java.util.Collection;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import net.stbbs.spring.jruby.SpringIntegratedJRubyRuntime;

import org.jruby.RubyNumeric;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

public class MIDISupport {
	static final int TICKS_PER_BEAT = 96;
	
	protected void parseMML(final SpringIntegratedJRubyRuntime ruby, IRubyObject self, String mml) throws InvalidMidiDataException
	{
		Track track = (Track)ruby.toJava(self);
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
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(ruby.getRuntime(), octave));
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
				self.setInstanceVariable("@notelen", RubyNumeric.int2fix(ruby.getRuntime(), notelen));
				continue;
			case 't':
				int t = 0;
				i++;
				while (i < mml.length() && Character.isDigit(mml.charAt(i))) {
					t *= 10;
					t += Integer.parseInt(Character.toString(mml.charAt(i)));
					i++;
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
				sm.setMessage(ShortMessage.PROGRAM_CHANGE, prg, 0);
				track.add(new MidiEvent(sm, position));
				continue;
			case '>':
				octave++;
				i++;
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(ruby.getRuntime(), octave));
				continue;
			case '<':
				octave--;
				i++;
				self.setInstanceVariable("@octave", RubyNumeric.int2fix(ruby.getRuntime(), octave));
				continue;
			default:
				throw ruby.newArgumentError("Invalida note char '" + c + "'");
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
					sm.setMessage(ShortMessage.NOTE_OFF, prevnoteno, 120);
					track.add(new MidiEvent(sm, position));
					tie = ruby.getNil();
					self.setInstanceVariable("@tie", tie);	// タイ終了
				}
			}
			if (noteno > 0) {
				if (tie == null || tie.isNil()) {	// タイ中じゃなければノートON
					ShortMessage sm = new ShortMessage();
					sm.setMessage(ShortMessage.NOTE_ON, noteno, 120);
					track.add(new MidiEvent(sm, position));
				} else {
					tie = ruby.getNil();
					self.setInstanceVariable("@tie", tie);	// タイ終了
				}
				
				position += ticks;

				if (i < mml.length() && mml.charAt(i) == '&') { // タイ
					tie = RubyNumeric.int2fix(ruby.getRuntime(), noteno);
					i++;
				} else {
					ShortMessage sm = new ShortMessage();
					sm.setMessage(ShortMessage.NOTE_OFF, noteno, 120);
					track.add(new MidiEvent(sm, position));
				}
			} else { // 休符
				position += ticks;
			}
			self.setInstanceVariable("@position", RubyNumeric.int2fix(ruby.getRuntime(), position));
		}
		
	}
	
	@ModuleMethod(arity=ModuleMethod.ARITY_OPTIONAL)
	public IRubyObject newSequence(final SpringIntegratedJRubyRuntime ruby,IRubyObject self, IRubyObject[] args, Block block) throws InvalidMidiDataException
	{
		Sequence sq =  new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
		IRubyObject rsq = ruby.toRuby(sq);
		rsq.getSingletonClass().defineMethod("createTrack", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				Sequence seq = (Sequence)ruby.toJava(self);
				IRubyObject rtr = ruby.toRuby(seq.createTrack());
				rtr.setInstanceVariable("@position", RubyNumeric.int2fix(ruby.getRuntime(), 0));
				rtr.setInstanceVariable("@octave", RubyNumeric.int2fix(ruby.getRuntime(), 4));
				rtr.setInstanceVariable("@notelen", RubyNumeric.int2fix(ruby.getRuntime(), TICKS_PER_BEAT));
				rtr.getSingletonClass().defineMethod("<<", new Callback() {
					public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
						try {
							for (IRubyObject arg:args) {
								if (arg instanceof Collection) {
									for (Object o:(Collection)arg) {
										String notes = o.toString();
										parseMML(ruby, self, notes);
									}
								} else {
									String notes = arg.asString().getUnicodeValue();
									parseMML(ruby, self, notes);
								}
							}
						} catch (InvalidMidiDataException e) {
							ruby.newArgumentError(e.getMessage());
						}
						return self;
					}
					public Arity getArity() { return Arity.ONE_ARGUMENT; }
				});
				
				if (block.isGiven()) {
					block.call(ruby.getCurrentContext(), new IRubyObject[] {rtr});
				}
				
				return rtr;
			}
			public Arity getArity() { return Arity.NO_ARGUMENTS; }

		});
		rsq.getSingletonClass().defineMethod("playSynchronous", new Callback() {
			public IRubyObject execute(IRubyObject self, IRubyObject[] args, Block block) {
				int tempo = 120;
				if (args.length > 0) {
					tempo = RubyNumeric.fix2int(args[0]);
				}
				Sequence seq = (Sequence)ruby.toJava(self);
				try {
					Sequencer sequencer = MidiSystem.getSequencer();
					sequencer.open();
					sequencer.setSequence(seq);
					sequencer.setTempoInBPM(tempo);
					sequencer.start();
					while (sequencer.isRunning()) Thread.sleep(100);
					sequencer.close();
				} catch (MidiUnavailableException e) {
					ruby.getRuntime().newRuntimeError(e.getMessage());
				} catch (InvalidMidiDataException e) {
					ruby.getRuntime().newRuntimeError(e.getMessage());
				} catch (InterruptedException e) {
					// Interrupted.
				}
				return self;
			}
			public Arity getArity() { return Arity.NO_ARGUMENTS; }
		});		
		if (block.isGiven()) {
			block.call(ruby.getCurrentContext(), new IRubyObject[] {rsq});
		}
		
		return rsq;
	}

}
