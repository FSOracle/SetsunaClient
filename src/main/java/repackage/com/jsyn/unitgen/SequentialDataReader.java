/*
 * Copyright 2009 Phil Burk, Mobileer Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repackage.com.jsyn.unitgen;

import repackage.com.jsyn.ports.UnitDataQueuePort;
import repackage.com.jsyn.ports.UnitInputPort;
import repackage.com.jsyn.ports.UnitOutputPort;

/**
 * Base class for reading a sample or envelope.
 * 
 * @author Phil Burk (C) 2009 Mobileer Inc
 */
public abstract class SequentialDataReader extends UnitGenerator {
    public UnitDataQueuePort dataQueue;
    public UnitInputPort amplitude;
    public UnitOutputPort output;

    public static final double DEFAULT_FREQUENCY = 440.0;
    public static final double DEFAULT_AMPLITUDE = 1.0;

    /* Define Unit Ports used by connect() and set(). */
    public SequentialDataReader() {
        addPort(dataQueue = new UnitDataQueuePort("Data"));
        addPort(amplitude = new UnitInputPort("Amplitude", DEFAULT_AMPLITUDE));
    }
}
