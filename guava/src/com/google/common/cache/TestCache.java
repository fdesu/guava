package com.google.common.cache;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class TestCache {

    private static final ExecutorService EXECUTOR = Executors
            .newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).build());

    static final LoadingCache<String, Long> THE_CACHE = CacheBuilder.newBuilder()
            .refreshAfterWrite(1, TimeUnit.MILLISECONDS)
            .removalListener(obj -> System.out.println(obj.getCause()))
            .build(new CacheLoader<String, Long>() {
                @Override
                public Long load(String s) throws InterruptedException {
                    Thread.sleep(200);
                    return System.currentTimeMillis();
                }
            });

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        THE_CACHE.getUnchecked("");
        Thread.sleep(100);
        EXECUTOR.submit(() -> THE_CACHE.get(""));
        for (int i = 0; i < 10; i++) {
            Thread.sleep(100);
            THE_CACHE.invalidateAll();
            System.out.println("Current size is: " + THE_CACHE.size());
//            THE_CACHE.invalidate("");
            System.out.println("Current time is: " + THE_CACHE.get(""));
        }
        Preconditions.checkArgument(System.currentTimeMillis() - THE_CACHE.get("") < 500);
    }

}