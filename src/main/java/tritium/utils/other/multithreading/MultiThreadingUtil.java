package tritium.utils.other.multithreading;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class MultiThreadingUtil {

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private MultiThreadingUtil() {
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        if (runnable == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Runnable is null"));
        }
        return CompletableFuture.runAsync(runnable, executor);
    }

    public static <T> T runOnMainThreadBlocking(Supplier<T> supplier) {
        if (Minecraft.getInstance().isSameThread()) {
            return supplier.get();
        }
        return Minecraft.getInstance().submit(supplier::get).join();
    }

    public static void runOnMainThread(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }
}
