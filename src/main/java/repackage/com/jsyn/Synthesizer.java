/*
 * Copyright 2010 Phil Burk, Mobileer Inc
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

package repackage.com.jsyn;

import repackage.com.jsyn.devices.AudioDeviceManager;
import repackage.com.jsyn.unitgen.ChannelOut;
import repackage.com.jsyn.unitgen.UnitGenerator;
import repackage.com.softsynth.shared.time.ScheduledCommand;
import repackage.com.softsynth.shared.time.TimeStamp;

/**
 * A synthesizer used by JSyn to generate audio. The synthesizer executes a network of unit
 * generators to create an audio signal.
 *
 * @author Phil Burk (C) 2010 Mobileer Inc
 */
public interface Synthesizer {

    int FRAMES_PER_BLOCK = 8;

    /**
     * Starts a background thread that generates audio using the default frame rate of 44100 and two
     * stereo output channels.
     */
    void start();

    /**
     * Starts a background thread that generates audio using the specified frame rate and two stereo
     * output channels.
     *
     * @param frameRate in Hertz
     */
    void start(int frameRate);

    /**
     * Starts the synthesizer using specific audio devices.
     * <p>
     * Note that using more than 2 channels will probably require the use of JPortAudio because
     * JavaSound currently does not support more than two channels.
     * JPortAudio is available at
     * <a href="http://www.softsynth.com/jsyn/developers/download.php">http://www.softsynth.com/jsyn/developers/download.php</a>.
     * <p>
     * or {@link ChannelOut}, which can be associated with any indexed channel.
     *
     * @param frameRate in Hertz
     * @param inputDeviceID obtained from an {@link AudioDeviceManager} or pass
     *            AudioDeviceManager.USE_DEFAULT_DEVICE
     * @param numInputChannels 0 for no input, 1 for mono, 2 for stereo, etcetera
     * @param ouputDeviceID obtained from an AudioDeviceManager or pass
     *            AudioDeviceManager.USE_DEFAULT_DEVICE
     * @param numOutputChannels 0 for no output, 1 for mono, 2 for stereo, etcetera
     */
    void start(int frameRate, int inputDeviceID, int numInputChannels, int ouputDeviceID,
               int numOutputChannels);

    /** @return JSyn version as a string */
    String getVersion();

    /** @return version as an integer that always increases */
    int getVersionCode();

    /** Stops the background thread that generates the audio. */
    void stop();

    /**
     * An AudioDeviceManager is an interface to audio hardware. It might be implemented using
     * JavaSound or a wrapper around PortAudio.
     *
     * @return audio device manager being used by the synthesizer.
     */
    AudioDeviceManager getAudioDeviceManager();

    /** @return the frame rate in samples per second */
    int getFrameRate();

    /**
     * Add a unit generator to the synthesizer so it can be played. This is required before starting
     * or connecting a unit generator into a network.
     *
     * @param ugen a unit generator to be executed by the synthesizer
     */
    void add(UnitGenerator ugen);

    /** Removes a unit generator added using add(). */
    void remove(UnitGenerator ugen);

    /** @return the current audio time in seconds */
    double getCurrentTime();

    /**
     * Start a unit generator at the specified time. This is not needed if a unit generator's output
     * is connected to other units. Typically you only need to start units that have no outputs, for
     * example LineOut or ChannelOut.
     */
    void startUnit(UnitGenerator unit, double time);

    void startUnit(UnitGenerator unit, TimeStamp timeStamp);

    /**
     * The startUnit and stopUnit methods are mainly for internal use.
     * Please call unit.start() or unit.stop() instead.
     * @param unit
     */
    void startUnit(UnitGenerator unit);

    void stopUnit(UnitGenerator unit, double time);

    void stopUnit(UnitGenerator unit, TimeStamp timeStamp);

    /**
     * The startUnit and stopUnit methods are mainly for internal use.
     * Please call unit.start() or unit.stop() instead.
     * @param unit
     */
    void stopUnit(UnitGenerator unit);

    /**
     * Sleep until the specified audio time is reached. In non-real-time mode, this will enable the
     * synthesizer to run.
     */
    void sleepUntil(double time) throws InterruptedException;

    /**
     * Sleep for the specified audio time duration. In non-real-time mode, this will enable the
     * synthesizer to run.
     */
    void sleepFor(double duration) throws InterruptedException;

    /**
     * If set true then the synthesizer will generate audio in real-time. Set it true for live
     * audio. If false then JSyn will run in non-real-time mode. This can be used to generate audio
     * to be written to a file. The default is true.
     *
     * @param realTime
     */
    void setRealTime(boolean realTime);

    /** Is JSyn running in real-time mode? */
    boolean isRealTime();

    /** Create a TimeStamp using the current audio time. */
    TimeStamp createTimeStamp();

    /** @return the current CPU usage as a fraction between 0.0 and 1.0 */
    double getUsage();

    /** @return inverse of frameRate, to avoid expensive divides */
    double getFramePeriod();

    /**
     * This count is not reset if you stop and restart.
     *
     * @return number of frames synthesized
     */
    long getFrameCount();

    /** Queue a command to be processed at a specific time in the background audio thread. */
    void scheduleCommand(TimeStamp timeStamp, ScheduledCommand command);

    /** Queue a command to be processed at a specific time in the background audio thread. */
    void scheduleCommand(double time, ScheduledCommand command);

    /** Queue a command to be processed as soon as possible in the background audio thread. */
    void queueCommand(ScheduledCommand command);

    /**
     * Clear all scheduled commands from the queue.
     * Commands will be discarded.
     */
    void clearCommandQueue();

    /**
     * @return true if the Synthesizer has been started
     */
    boolean isRunning();

    /**
     * Add a task that will be run repeatedly on the Audio Thread before it generates every new block of Audio.
     * This task must be very quick and should not perform any blocking operations. If you are not
     * certain that you need an Audio rate task then don't use this.
     *
     * @param task
     */
    void addAudioTask(Runnable task);

    void removeAudioTask(Runnable task);

}
