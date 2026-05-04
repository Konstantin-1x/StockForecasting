package org.example.parser.wb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class WildberriesParserExecutionConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService wildberriesParserExecutor(WildberriesParserProperties properties) {
        int threads = Math.max(1, properties.getScanParallelism());
        AtomicInteger sequence = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "wb-parser-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(threads, threadFactory);
    }
}
