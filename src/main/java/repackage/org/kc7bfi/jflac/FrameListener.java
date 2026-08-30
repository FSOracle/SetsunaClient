/*
 * Created on Jun 2, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package repackage.org.kc7bfi.jflac;

import repackage.org.kc7bfi.jflac.frame.Frame;
import repackage.org.kc7bfi.jflac.metadata.Metadata;

/**
 * FrameListener interface.
 * This interface defines the singatures for a class to listen
 * for frame events from the Decoder.
 *
 * @author kc7bfi
 */
public interface FrameListener {

    /**
     * Called for each Metadata frame read.
     *
     * @param metadata The metadata frame read
     */
    void processMetadata(Metadata metadata);

    /**
     * Called for each data frame read.
     *
     * @param frame The data frame read
     */
    void processFrame(Frame frame);

    /**
     * Called for each frame error detected.
     *
     * @param msg The error message
     */
    void processError(String msg);
}
