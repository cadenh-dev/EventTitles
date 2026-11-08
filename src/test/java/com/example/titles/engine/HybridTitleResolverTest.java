package com.example.titles.engine;

import static org.junit.jupiter.api.Assertions.*;
import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.TitlesSettings;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HybridTitleResolverTest {
    private static TitlesSettings config(boolean enabled,int timeout,String fallback,int max){TitlesSettings d=TitlesSettings.defaults();return new TitlesSettings(enabled,d.llmProvider(),d.llmEndpoint(),d.llmModel(),d.llmApiKey(),timeout,d.llmTemperature(),max,5,fallback);}
    private EventContext ctx(EventCategory cat,String subject){return new EventContext(UUID.randomUUID(),"tester",cat,subject,"PLAINS","AIR","NONE",1,"");}
    private HybridTitleResolver resolver(LlmTitleGenerator llm,TitlesSettings settings){return new HybridTitleResolver(new StaticTitleRegistry(),llm,()->settings);}
    private Optional<AwardedTitle> resolve(HybridTitleResolver r,EventContext c) throws Exception{return r.resolve(c).get(4,TimeUnit.SECONDS);}
    @Test void dragonShortCircuits(){AtomicBoolean called=new AtomicBoolean();var r=resolver(c->{called.set(true);return CompletableFuture.completedFuture(Optional.empty());},config(true,1,"fallback",-1));assertEquals(100,assertDoesNotThrow(()->resolve(r,ctx(EventCategory.BOSS_KILL,"ENDER_DRAGON"))).orElseThrow().priority());assertFalse(called.get());}
    @Test void llmTitleUsedAndPriorityLifted(){var r=resolver(c->CompletableFuture.completedFuture(Optional.of(new AwardedTitle("<red>Nice</red>",3,true))),config(true,1,"fallback",-1));assertEquals(40,assertDoesNotThrow(()->resolve(r,ctx(EventCategory.MINING,"ANCIENT_DEBRIS"))).orElseThrow().priority());}
    @Test void llmTitleUsedForNonStaticEvent(){var r=resolver(c->CompletableFuture.completedFuture(Optional.of(new AwardedTitle("<red>Fresh</red>",42,true))),config(true,1,"fallback",-1));assertEquals("<red>Fresh</red>",assertDoesNotThrow(()->resolve(r,ctx(EventCategory.SHENANIGANS,"UNKNOWN"))).orElseThrow().miniMessageText());}
    @Test void failuresTimeoutAndDisabledUseStaticFallback(){LlmTitleGenerator failure=c->CompletableFuture.failedFuture(new RuntimeException());assertEquals(30,assertDoesNotThrow(()->resolve(resolver(failure,config(true,1,"fallback",-1)),ctx(EventCategory.DEATH_MOB,"CREEPER"))).orElseThrow().priority());long start=System.nanoTime();LlmTitleGenerator hanging=c->new CompletableFuture<>();assertEquals(30,assertDoesNotThrow(()->resolve(resolver(hanging,config(true,1,"fallback",-1)),ctx(EventCategory.DEATH_MOB,"CREEPER"))).orElseThrow().priority());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500);assertEquals(20,assertDoesNotThrow(()->resolve(resolver(failure,config(false,1,"fallback",-1)),ctx(EventCategory.DEATH_ENV,"FALL"))).orElseThrow().priority());}
    @Test void defaultsWhenNoStaticOrLlmTitle(){var r=resolver(c->CompletableFuture.completedFuture(Optional.empty()),config(true,1,"<gray>Default</gray>",-1));AwardedTitle title=assertDoesNotThrow(()->resolve(r,ctx(EventCategory.SHENANIGANS,"UNKNOWN"))).orElseThrow();assertEquals(5,title.priority());assertEquals("<gray>Default</gray>",title.miniMessageText());}
}
