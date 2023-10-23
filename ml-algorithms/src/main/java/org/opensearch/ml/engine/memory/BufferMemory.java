/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.memory.Message;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BufferMemory implements Memory {
    public static final String TYPE = "buffer";
    protected Map<String, Queue<Message>> messages;

    public BufferMemory() {
        this.messages = new ConcurrentHashMap<>();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void save(String id, Message message) {
        Queue<Message> messageQueue = messages.computeIfAbsent(id, (k) -> new ConcurrentLinkedDeque<>());
        messageQueue.add(message);
    }

    @Override
    public Message[] getMessages(String id) {
        if (!messages.containsKey(id)) {
            return null;
        }
        return messages.get(id).toArray(new Message[0]);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    @Override
    public void remove(String id) {
        messages.remove(id);
    }

}
