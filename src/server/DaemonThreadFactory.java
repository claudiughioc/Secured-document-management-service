package server;

import java.util.concurrent.ThreadFactory;

/**
 * Custom thread factory used in connection pool
 */
public final class DaemonThreadFactory implements ThreadFactory {
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r);
		thread.setDaemon(true);
		return thread;
	}
}
