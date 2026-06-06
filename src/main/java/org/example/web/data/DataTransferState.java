package org.example.web.data;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DataTransferState {

    private final AtomicBoolean transferInProgress = new AtomicBoolean(false);
    private final AtomicReference<String> operation = new AtomicReference<>();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();

    public TransferLock begin(String operationName) {
        if (!transferInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Сейчас уже выполняется перенос данных: " + operation.get());
        }
        operation.set(operationName);
        startedAt.set(Instant.now());
        return new TransferLock();
    }

    public boolean isTransferInProgress() {
        return transferInProgress.get();
    }

    public String currentOperation() {
        return operation.get();
    }

    public Instant startedAt() {
        return startedAt.get();
    }

    public final class TransferLock implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            operation.set(null);
            startedAt.set(null);
            transferInProgress.set(false);
        }
    }
}
