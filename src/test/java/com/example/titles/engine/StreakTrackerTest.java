package com.example.titles.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.example.titles.api.TitlesSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreakTrackerTest {
    private static TitlesSettings settings(int minutes){TitlesSettings d=TitlesSettings.defaults();return new TitlesSettings(d.llmEnabled(),d.llmProvider(),d.llmEndpoint(),d.llmModel(),d.llmApiKey(),d.llmTimeoutSeconds(),d.llmTemperature(),d.maxTitleLength(),minutes,d.defaultTitle());}
    private static final class MutableClock extends Clock {
        private Instant instant=Instant.parse("2026-01-01T00:00:00Z");
        void advance(long seconds){instant=instant.plusSeconds(seconds);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return instant;}
    }
    @Test void countsWithinWindowAndExpiresOutsideIt(){MutableClock clock=new MutableClock();StreakTracker tracker=new StreakTracker(()->settings(2),clock);UUID id=UUID.randomUUID();assertEquals(1,tracker.recordAndGet(id,"a"));clock.advance(120);assertEquals(2,tracker.recordAndGet(id,"a"));clock.advance(121);assertEquals(1,tracker.recordAndGet(id,"a"));}
    @Test void keyChangeResetsAndResetClears(){StreakTracker tracker=new StreakTracker(()->settings(5),new MutableClock());UUID id=UUID.randomUUID();assertEquals(1,tracker.recordAndGet(id,"a"));assertEquals(1,tracker.recordAndGet(id,"b"));assertEquals(2,tracker.recordAndGet(id,"b"));tracker.reset(id);assertEquals(1,tracker.recordAndGet(id,"b"));}
    @Test void concurrentIncrementsAreAtomic() throws Exception {StreakTracker tracker=new StreakTracker(()->settings(5),new MutableClock());UUID id=UUID.randomUUID();int count=400;ExecutorService pool=Executors.newFixedThreadPool(12);CountDownLatch start=new CountDownLatch(1),done=new CountDownLatch(count);for(int i=0;i<count;i++)pool.execute(()->{try{start.await();tracker.recordAndGet(id,"same");}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{done.countDown();}});start.countDown();org.junit.jupiter.api.Assertions.assertTrue(done.await(10,TimeUnit.SECONDS));pool.shutdownNow();assertEquals(count+1,tracker.recordAndGet(id,"same"));}
}
