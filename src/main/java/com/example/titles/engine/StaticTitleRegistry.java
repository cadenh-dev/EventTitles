package com.example.titles.engine;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventCategory;
import com.example.titles.api.EventContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class StaticTitleRegistry {
    private record Rule(String id, EventCategory category, Predicate<EventContext> matcher, String title, int priority) {}
    private final List<Rule> rules;

    public StaticTitleRegistry() {
        List<Rule> r = new ArrayList<>();
        add(r,"dragon_slayer",EventCategory.BOSS_KILL,c->subject(c,"ENDER_DRAGON"),"<gradient:#7b2ff7:#f107a3><bold>Dragonslayer</bold></gradient>",100);
        add(r,"withered",EventCategory.BOSS_KILL,c->subject(c,"WITHER"),"<gradient:#555555:#aaaaaa><bold>Withered</bold></gradient>",95);
        add(r,"warden_whisperer",EventCategory.BOSS_KILL,c->subject(c,"WARDEN"),"<dark_aqua><bold>Warden Whisperer</bold></dark_aqua>",90);
        add(r,"elder_ender",EventCategory.BOSS_KILL,c->subject(c,"ELDER_GUARDIAN"),"<aqua>Elder Ender</aqua>",85);
        add(r,"boss_breeze",EventCategory.BOSS_KILL,c->subject(c,"BREEZE"),"<gradient:#a8edea:#fed6e3>Breeze Tamer</gradient>",76);
        add(r,"creeper_consecutive",EventCategory.DEATH_MOB,c->subject(c,"CREEPER")&&c.streakCount()>=3,"<gradient:#b7f8db:#50a7c2><bold>Creeper Magnet</bold></gradient>",80);
        add(r,"creeper_hugger",EventCategory.DEATH_MOB,c->subject(c,"CREEPER"),"<green>Creeper Hugger</green>",30);
        add(r,"zombie_snack",EventCategory.DEATH_MOB,c->subject(c,"ZOMBIE"),"<dark_green>Zombie Snack</dark_green>",25);
        add(r,"skeleton_target",EventCategory.DEATH_MOB,c->subject(c,"SKELETON"),"<gray>Arrow Magnet</gray>",25);
        add(r,"spider_supper",EventCategory.DEATH_MOB,c->subject(c,"SPIDER"),"<dark_purple>Webbed Wonder</dark_purple>",26);
        add(r,"enderman_gaze",EventCategory.DEATH_MOB,c->subject(c,"ENDERMAN"),"<dark_purple>Gaze Collector</dark_purple>",32);
        add(r,"brute_force",EventCategory.DEATH_MOB,c->subject(c,"PIGLIN_BRUTE"),"<gold>Brute Force</gold>",38);
        add(r,"guardian_gulp",EventCategory.DEATH_MOB,c->subject(c,"ELDER_GUARDIAN"),"<aqua>Ocean's Snack</aqua>",45);
        add(r,"deep_dark_victim",EventCategory.DEATH_MOB,c->c.biome().equalsIgnoreCase("DEEP_DARK"),"<dark_gray>Darkness Dweller</dark_gray>",50);
        add(r,"mob_naked",EventCategory.DEATH_MOB,c->c.armorSummary().equalsIgnoreCase("NONE"),"<yellow>Fashionably Fragile</yellow>",29);
        add(r,"mob_totem",EventCategory.DEATH_MOB,c->c.heldItem().equalsIgnoreCase("TOTEM_OF_UNDYING"),"<gold>Totem Tease</gold>",33);
        add(r,"mob_outplayed",EventCategory.DEATH_MOB,c->subject(c,"PLAYER"),"<red>Thoroughly Outplayed</red>",34);
        add(r,"fall_from_grace",EventCategory.DEATH_ENV,c->subject(c,"FALL"),"<gold>Gravity's Favorite</gold>",20);
        add(r,"void_vacation",EventCategory.DEATH_ENV,c->subject(c,"VOID"),"<dark_purple>Void Vacation</dark_purple>",25);
        add(r,"lava_lounge",EventCategory.DEATH_ENV,c->subject(c,"LAVA"),"<red>Lava Lounge</red>",22);
        add(r,"fire_starter",EventCategory.DEATH_ENV,c->subject(c,"FIRE"),"<gold>Well Done</gold>",18);
        add(r,"drowned",EventCategory.DEATH_ENV,c->subject(c,"DROWNING"),"<blue>Bubble Breaker</blue>",19);
        add(r,"suffocated",EventCategory.DEATH_ENV,c->subject(c,"SUFFOCATION"),"<gray>Wall Hugger</gray>",17);
        add(r,"starving",EventCategory.DEATH_ENV,c->subject(c,"STARVATION"),"<yellow>Snack Seeker</yellow>",16);
        add(r,"frozen_solid",EventCategory.DEATH_ENV,c->subject(c,"FREEZE"),"<aqua>Ice Cold</aqua>",21);
        add(r,"lightning_rod",EventCategory.DEATH_ENV,c->subject(c,"LIGHTNING"),"<yellow>Storm Magnet</yellow>",23);
        add(r,"cactus_kissed",EventCategory.DEATH_ENV,c->subject(c,"CACTUS"),"<green>Prickly Finish</green>",15);
        add(r,"berry_bush",EventCategory.DEATH_ENV,c->subject(c,"SWEET_BERRY_BUSH"),"<dark_green>Berry Bruised</dark_green>",14);
        add(r,"wall_flight",EventCategory.DEATH_ENV,c->subject(c,"FLY_INTO_WALL"),"<gray>Wall Pilot</gray>",24);
        add(r,"entity_boom",EventCategory.DEATH_ENV,c->subject(c,"ENTITY_EXPLOSION"),"<red>Blast Radius</red>",26);
        add(r,"pvp_streak",EventCategory.PVP_KILL,c->c.streakCount()>=5,"<gradient:#f6d365:#fda085><bold>Unstoppable</bold></gradient>",75);
        add(r,"pvp_sword",EventCategory.PVP_KILL,c->c.heldItem().endsWith("SWORD"),"<red>Blade Runner</red>",48);
        add(r,"pvp_axe",EventCategory.PVP_KILL,c->c.heldItem().endsWith("AXE"),"<gold>Axe Marksman</gold>",46);
        add(r,"pvp_naked",EventCategory.PVP_KILL,c->c.armorSummary().equalsIgnoreCase("NONE"),"<yellow>Brave Threads</yellow>",43);
        add(r,"pvp_deepdark",EventCategory.PVP_KILL,c->c.biome().equalsIgnoreCase("DEEP_DARK"),"<dark_aqua>Dark Duelist</dark_aqua>",52);
        add(r,"clutch_heart",EventCategory.CLUTCH_SURVIVAL,c->number(c,"health_after",2)<=1,"<gradient:#ff512f:#dd2476><bold>Heartbreaker</bold></gradient>",70);
        add(r,"clutch_void",EventCategory.CLUTCH_SURVIVAL,c->subject(c,"VOID"),"<light_purple>Void Dodger</light_purple>",62);
        add(r,"clutch_lava",EventCategory.CLUTCH_SURVIVAL,c->subject(c,"LAVA"),"<gold>Hot Foot</gold>",58);
        add(r,"clutch_water",EventCategory.CLUTCH_SURVIVAL,c->c.heldItem().contains("WATER_BUCKET"),"<blue>Bucket Legend</blue>",65);
        add(r,"clutch_totem",EventCategory.CLUTCH_SURVIVAL,c->c.heldItem().equalsIgnoreCase("TOTEM_OF_UNDYING"),"<gold>Not Today</gold>",60);
        add(r,"ancient_debris",EventCategory.MINING,c->subject(c,"ANCIENT_DEBRIS"),"<gradient:#f7971e:#ffd200><bold>Ancient Prospector</bold></gradient>",40);
        add(r,"diamond_digger",EventCategory.MINING,c->subject(c,"DIAMOND_ORE")||subject(c,"DEEPSLATE_DIAMOND_ORE"),"<aqua>Diamond Digger</aqua>",35);
        add(r,"spawner_smasher",EventCategory.MINING,c->subject(c,"SPAWNER"),"<dark_purple>Spawner Smasher</dark_purple>",35);
        add(r,"mining_low_y",EventCategory.MINING,c->number(c,"y",999)<=-50,"<dark_gray>Deep Delver</dark_gray>",32);
        add(r,"rotten_feast",EventCategory.SHENANIGANS,c->subject(c,"ROTTEN_FLESH"),"<green>Rotten Gourmet</green>",10);
        add(r,"suspicious_snack",EventCategory.SHENANIGANS,c->subject(c,"SUSPICIOUS_STEW"),"<light_purple>Stew Ponderer</light_purple>",18);
        add(r,"potato_pro",EventCategory.SHENANIGANS,c->subject(c,"POISONOUS_POTATO"),"<green>Potato Risk-Taker</green>",20);
        add(r,"puffer_puffer",EventCategory.SHENANIGANS,c->subject(c,"PUFFERFISH"),"<yellow>Pointy Palate</yellow>",19);
        add(r,"cake_connoisseur",EventCategory.SHENANIGANS,c->subject(c,"CAKE"),"<gold>Cake Connoisseur</gold>",16);
        add(r,"enchanted_appetite",EventCategory.SHENANIGANS,c->subject(c,"ENCHANTED_GOLDEN_APPLE"),"<gradient:#fceabb:#f8b500>Golden Appetite</gradient>",24);
        rules = List.copyOf(r);
    }

    private static void add(List<Rule> rules,String id,EventCategory cat,Predicate<EventContext> matcher,String title,int priority){rules.add(new Rule(id,cat,matcher,title,priority));}
    private static boolean subject(EventContext c,String value){return c.primarySubject().equalsIgnoreCase(value);}
    private static double number(EventContext c,String key,double fallback){try{return Double.parseDouble(metadata(c).getOrDefault(key,Double.toString(fallback)));}catch(NumberFormatException e){return fallback;}}
    private static Map<String,String> metadata(EventContext c){java.util.HashMap<String,String> values=new java.util.HashMap<>();for(String part:c.extraMetadata().split(";")){int i=part.indexOf('=');if(i>0)values.put(part.substring(0,i).trim(),part.substring(i+1).trim());}return values;}

    public Optional<AwardedTitle> findStaticMatch(EventContext ctx) {
        return rules.stream().filter(rule -> rule.category()==ctx.category() && rule.matcher().test(ctx))
            .max((a,b)->Integer.compare(a.priority(),b.priority())).map(rule->new AwardedTitle(rule.title(),rule.priority(),false));
    }
    public int size(){return rules.size();}
    List<String> ruleIdsForTest(){return rules.stream().map(Rule::id).toList();}
    List<Integer> prioritiesForTest(){return rules.stream().map(Rule::priority).toList();}
    List<String> titlesForTest(){return rules.stream().map(Rule::title).toList();}
    Map<EventCategory,Long> categoryCountsForTest(){return rules.stream().collect(java.util.stream.Collectors.groupingBy(Rule::category,java.util.stream.Collectors.counting()));}
}
