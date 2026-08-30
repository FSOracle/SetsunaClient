/*
 * Created on Jun 2, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package repackage.org.kc7bfi.jflac;

import repackage.org.kc7bfi.jflac.metadata.StreamInfo;
import repackage.org.kc7bfi.jflac.util.ByteData;

/**
 * PCMProcessor interface.
 * This interface defines the signatures for a class to listen
 * to PCM decode events.
 *
 * @author kc7bfi
 */
public interface PCMProcessor {
    /**
     * Called when StreamInfo read.
     *
     * @param streamInfo The FLAC stream info metadata block
     */
    void processStreamInfo(StreamInfo streamInfo);

    /**
     * Called when each data frame is decompressed.
     *
     * @param pcm The decompressed PCM data
     */
    void processPCM(ByteData pcm);

    /** returns true if process has to be canceled
     *
     * @return
     */
    // public boolean isCanceled();
}
