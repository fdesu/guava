/*
 * Copyright (C) 2017 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.cache;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.TestingCacheLoaders.IncrementingLoader;
import com.google.common.testing.FakeTicker;
import junit.framework.TestCase;
import org.junit.Test;

import static com.google.common.cache.TestingCacheLoaders.incrementingLoader;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Tests relating to automatic cache refreshing.
 *
 * @author Charles Fry
 */
public class CacheRefreshTest extends TestCase {
  public void testAutoRefresh() {
    FakeTicker ticker = new FakeTicker();
    IncrementingLoader loader = incrementingLoader();
    LoadingCache<Integer, Integer> cache = CacheBuilder.newBuilder()
        .refreshAfterWrite(3, MILLISECONDS)
        .expireAfterWrite(6, MILLISECONDS)
        .lenientParsing()
        .ticker(ticker)
        .build(loader);
    int expectedLoads = 0;
    int expectedReloads = 0;
    for (int i = 0; i < 3; i++) {
      assertEquals(Integer.valueOf(i), cache.getUnchecked(i));
      expectedLoads++;
      assertEquals(expectedLoads, loader.getLoadCount());
      assertEquals(expectedReloads, loader.getReloadCount());
      ticker.advance(1, MILLISECONDS);
    }

    assertEquals(Integer.valueOf(0), cache.getUnchecked(0));
    assertEquals(Integer.valueOf(1), cache.getUnchecked(1));
    assertEquals(Integer.valueOf(2), cache.getUnchecked(2));
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());

    // refresh 0
    ticker.advance(1, MILLISECONDS);
    assertEquals(Integer.valueOf(1), cache.getUnchecked(0));
    expectedReloads++;
    assertEquals(Integer.valueOf(1), cache.getUnchecked(1));
    assertEquals(Integer.valueOf(2), cache.getUnchecked(2));
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());

    // write to 1 to delay its refresh
    cache.asMap().put(1, -1);
    ticker.advance(1, MILLISECONDS);
    assertEquals(Integer.valueOf(1), cache.getUnchecked(0));
    assertEquals(Integer.valueOf(-1), cache.getUnchecked(1));
    assertEquals(Integer.valueOf(2), cache.getUnchecked(2));
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());

    // refresh 2
    ticker.advance(1, MILLISECONDS);
    assertEquals(Integer.valueOf(1), cache.getUnchecked(0));
    assertEquals(Integer.valueOf(-1), cache.getUnchecked(1));
    assertEquals(Integer.valueOf(3), cache.getUnchecked(2));
    expectedReloads++;
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());

    ticker.advance(1, MILLISECONDS);
    assertEquals(Integer.valueOf(1), cache.getUnchecked(0));
    assertEquals(Integer.valueOf(-1), cache.getUnchecked(1));
    assertEquals(Integer.valueOf(3), cache.getUnchecked(2));
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());

    // refresh 0 and 1
    ticker.advance(1, MILLISECONDS);
    assertEquals(Integer.valueOf(2), cache.getUnchecked(0));
    expectedReloads++;
    assertEquals(Integer.valueOf(0), cache.getUnchecked(1));
    expectedReloads++;
    assertEquals(Integer.valueOf(3), cache.getUnchecked(2));
    assertEquals(expectedLoads, loader.getLoadCount());
    assertEquals(expectedReloads, loader.getReloadCount());
  }

  /**
   * Test simulates a race condition when Thread1 tries to load value (long-running load),
   * invalidateAll happens and Thread2 also tries to load value
   *
   * @throws InterruptedException
   */
  @Test
  public void testCacheContinuesToRefreshValueAfterInvalidateClearsTheCache()
          throws InterruptedException, ExecutionException {
    AtomicInteger counter = new AtomicInteger();
    LoadingCache<String, Integer> cache = CacheBuilder.newBuilder()
            .refreshAfterWrite(1, TimeUnit.MILLISECONDS)
            .removalListener(obj -> System.out.println(obj.getCause()))
            .build(new CacheLoader<String, Integer>() {
              @Override
              public Integer load(String s) throws InterruptedException {
                Thread.sleep(10L);
                return counter.incrementAndGet();
              }
            });

    cache.getUnchecked("");

    Thread thread = new Thread(() -> {
      try {
        cache.get("", () -> {
          Thread.sleep(100L);
          return counter.incrementAndGet();
        });
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    });
    thread.start();

    Set<Integer> valuesAfterRefresh = new HashSet<>(20);
    for (int i = 0; i < 20; i++) {
      Thread.sleep(50L);
      cache.invalidateAll();
      Integer e = cache.get("");
      System.out.println("Retrived: " + e);
      valuesAfterRefresh.add(e);
    }
    System.out.println(valuesAfterRefresh);
    assertEquals("Value should be refreshed", 20, valuesAfterRefresh.size());
  }

}
