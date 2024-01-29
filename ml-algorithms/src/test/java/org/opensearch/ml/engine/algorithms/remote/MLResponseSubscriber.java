///*
// *
// *  * Copyright OpenSearch Contributors
// *  * SPDX-License-Identifier: Apache-2.0
// *
// */
//
//package org.opensearch.ml.engine.algorithms.remote;
//
//import org.opensearch.OpenSearchStatusException;
//import org.opensearch.core.rest.RestStatus;
//import org.reactivestreams.Subscriber;
//import org.reactivestreams.Subscription;
//
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//
//public class MLResponseSubscriber implements Subscriber<ByteBuffer> {
//    private Subscription subscription;
//    @Override
//    public void onSubscribe(Subscription s) {
//        this.subscription = s;
//        s.request(Long.MAX_VALUE);
//    }
//
//    @Override
//    public void onNext(ByteBuffer byteBuffer) {
//        responseBody.append(StandardCharsets.UTF_8.decode(byteBuffer));
//        subscription.request(Long.MAX_VALUE);
//    }
//    @Override public void onError(Throwable t) {
//        countDownLatch.getCountDownLatch().countDown();
//        log.error("Error on receiving response body from remote: {}", t instanceof NullPointerException ? "NullPointerException" : t.getMessage(), t);
//        errorMsg.add("Error on receiving response body from remote: " + (t instanceof NullPointerException ? "NullPointerException" : t.getMessage()));
//        if (countDownLatch.getCountDownLatch().getCount() == 0) {
//            actionListener.onFailure(new OpenSearchStatusException("Error on receiving response body from remote: " + String.join(",", errorMsg), RestStatus.INTERNAL_SERVER_ERROR));
//        } else {
//            log.debug("Not all responses received, left response count is: " + countDownLatch.getCountDownLatch().getCount());
//        }
//    }
//
//    @Override
//    public void onComplete() {
//        try {
//            String fullResponseBody = responseBody.toString();
//            processResponse(statusCode, fullResponseBody, parameters, tensorOutputs);
//            countDownLatch.getCountDownLatch().countDown();
//            if (countDownLatch.getCountDownLatch().getCount() == 0) {
//                log.debug("All responses received, calling action listener to return final results.");
//                actionListener.onResponse(reOrderTensorResponses(tensorOutputs));
//            }
//        } catch (Throwable e) {
//            countDownLatch.getCountDownLatch().countDown();
//            log.error("Error on processing response from remote: {}", e instanceof NullPointerException ? "NullPointerException" : e.getMessage(), e);
//            errorMsg.add("Error on receiving response from remote: " + (e instanceof NullPointerException ? "NullPointerException" : e.getMessage()));
//            if (countDownLatch.getCountDownLatch().getCount() == 0) {
//                actionListener.onFailure(new OpenSearchStatusException("Error on receiving response from remote: " + String.join(",", errorMsg), RestStatus.INTERNAL_SERVER_ERROR));
//            } else {
//                log.debug("Not all responses received, left response count is: " + countDownLatch.getCountDownLatch().getCount());
//            }
//        }
//    }
//}
