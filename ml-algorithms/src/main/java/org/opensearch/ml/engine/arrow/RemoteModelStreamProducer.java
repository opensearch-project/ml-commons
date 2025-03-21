package org.opensearch.ml.engine.arrow;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.opensearch.arrow.spi.StreamProducer;
import org.opensearch.common.unit.TimeValue;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class RemoteModelStreamProducer implements StreamProducer<VectorSchemaRoot, BufferAllocator> {
    volatile boolean isClosed = false;
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    TimeValue deadline = TimeValue.timeValueSeconds(5);
    private volatile boolean produceError = false;

    @Getter
    private AtomicBoolean isStop = new AtomicBoolean(false);
    @Getter
    private ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void setProduceError(boolean produceError) {
        this.produceError = produceError;
    }

    public RemoteModelStreamProducer() {}

    VectorSchemaRoot root;

    @Override
    public VectorSchemaRoot createRoot(BufferAllocator allocator) {
        VarCharVector eventVector = new VarCharVector("event", allocator);
        FieldVector[] vectors = new FieldVector[] { eventVector };
        root = new VectorSchemaRoot(Arrays.asList(vectors));
        return root;
    }

    @Override
    public BatchedJob<VectorSchemaRoot> createJob(BufferAllocator allocator) {
        return new BatchedJob<>() {
            @Override
            public void run(VectorSchemaRoot root, FlushSignal flushSignal) {
                VarCharVector eventVector = (VarCharVector) root.getVector("event");
                root.setRowCount(1);
                while (true) {
                    if (produceError) {
                        throw new RuntimeException("Server error while producing batch");
                    }
                    if (queue.isEmpty() && isStop.get()) {
                        log.info("The end of streaming response.");
                        return;
                    }
                    String event = queue.poll();
                    if (event != null) {
                        eventVector.setSafe(0, event.getBytes(StandardCharsets.UTF_8));
                        flushSignal.awaitConsumption(TimeValue.timeValueMillis(1000));
                        eventVector.clear();
                        root.setRowCount(1);
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("sleep failure");
                        }
                    }
                }
            }

            @Override
            public void onCancel() {
                root.close();
                isClosed = true;
            }

            @Override
            public boolean isCancelled() {
                return isClosed;
            }
        };
    }

    @Override
    public TimeValue getJobDeadline() {
        return deadline;
    }

    @Override
    public int estimatedRowCount() {
        return 100;
    }

    @Override
    public String getAction() {
        return "";
    }

    @Override
    public void close() {
        root.close();
        closeLatch.countDown();
        isClosed = true;
    }

    public boolean waitForClose(long timeout, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(timeout, unit);
    }
}
