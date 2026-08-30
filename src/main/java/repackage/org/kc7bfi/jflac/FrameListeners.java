/*
 * Created on Jun 28, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package repackage.org.kc7bfi.jflac;

import repackage.org.kc7bfi.jflac.frame.Frame;
import repackage.org.kc7bfi.jflac.metadata.Metadata;

import java.util.HashSet;


/**
 * Class to handle frame listeners.
 *
 * @author kc7bfi
 */
class FrameListeners implements FrameListener {
    private final HashSet frameListeners = new HashSet();

    /**
     * Add a frame listener.
     *
     * @param listener The frame listener to add
     */
    public void addFrameListener(FrameListener listener) {
        synchronized (frameListeners) {
            frameListeners.add(listener);
        }
    }

    /**
     * Remove a frame listener.
     *
     * @param listener The frame listener to remove
     */
    public void removeFrameListener(FrameListener listener) {
        synchronized (frameListeners) {
            frameListeners.remove(listener);
        }
    }

    /**
     * Process metadata records.
     *
     * @param metadata the metadata block
     * @see FrameListener#processMetadata(org.kc7bfi.jflac.metadata.MetadataBase)
     */
    public void processMetadata(Metadata metadata) {
        synchronized (frameListeners) {
            for (Object frameListener : frameListeners) {
                FrameListener listener = (FrameListener) frameListener;
                listener.processMetadata(metadata);
            }
        }
    }

    /**
     * Process data frames.
     *
     * @param frame the data frame
     * @see FrameListener#processFrame(Frame)
     */
    public void processFrame(Frame frame) {
        synchronized (frameListeners) {
            for (Object frameListener : frameListeners) {
                FrameListener listener = (FrameListener) frameListener;
                listener.processFrame(frame);
            }
        }
    }

    /**
     * Called for each frame error detected.
     *
     * @param msg The error message
     * @see FrameListener#processError(String)
     */
    public void processError(String msg) {
        synchronized (frameListeners) {
            for (Object frameListener : frameListeners) {
                FrameListener listener = (FrameListener) frameListener;
                listener.processError(msg);
            }
        }
    }

}
