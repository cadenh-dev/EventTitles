package com.example.titles.engine;

import static org.junit.jupiter.api.Assertions.*;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import com.example.titles.api.TitleText;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaticTitleRegistryTest {
    private final StaticTitleRegistry registry=new StaticTitleRegistry();
    private EventContext context(EventCategory category,String subject,int streak,String metadata,String biome,String item,String armor){return new EventContext(UUID.randomUUID(),"tester",category,subject,biome,item,armor,streak,metadata);}
    @Test void containsFiftyUniqueRulesAcrossEveryCategory(){assertEquals(50,registry.size());assertEquals(50,registry.ruleIdsForTest().stream().distinct().count());assertEquals(50,registry.titlesForTest().stream().distinct().count());for(EventCategory c:EventCategory.values())assertTrue(registry.categoryCountsForTest().getOrDefault(c,0L)>0);for(String title:registry.titlesForTest()){assertFalse(TitleText.stripTags(title).isBlank());assertFalse(title.contains("§"));}}
    @Test void requiredPrioritiesArePresent(){assertEquals(100,registry.findStaticMatch(context(EventCategory.BOSS_KILL,"ENDER_DRAGON",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(95,registry.findStaticMatch(context(EventCategory.BOSS_KILL,"WITHER",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(90,registry.findStaticMatch(context(EventCategory.BOSS_KILL,"WARDEN",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(80,registry.findStaticMatch(context(EventCategory.DEATH_MOB,"CREEPER",3,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(30,registry.findStaticMatch(context(EventCategory.DEATH_MOB,"CREEPER",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(20,registry.findStaticMatch(context(EventCategory.DEATH_ENV,"FALL",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());assertEquals(10,registry.findStaticMatch(context(EventCategory.SHENANIGANS,"ROTTEN_FLESH",1,"","PLAINS","AIR","NONE")).orElseThrow().priority());}
}
