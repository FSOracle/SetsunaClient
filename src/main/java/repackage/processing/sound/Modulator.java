package repackage.processing.sound;

import repackage.com.jsyn.ports.UnitOutputPort;

/**
 * Interface for any object that can be passed as a modulator to an oscillator's 
 * freq() and amp() methods.
 */
public interface Modulator {

	UnitOutputPort getModulator();

}
