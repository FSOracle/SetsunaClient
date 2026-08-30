package tritium.utils.other;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;

public class WrappedInputStream extends InputStream {

    private final InputStream in;
    private final ProgressListener listener;

    @SneakyThrows
    public WrappedInputStream(InputStream in, ProgressListener l) {
        this.in = in;
        this.listener = l;
    }

    public int read = 0;

    @Override
    public int read() throws IOException {
        int read = this.in.read();

        this.listener.onProgress((++this.read) / (double) this.in.available());
        this.listener.bytesRead(this.read);

        return read;
    }

    public interface ProgressListener {

        void onProgress(double progress);

        void bytesRead(int bytesRead);

    }

}