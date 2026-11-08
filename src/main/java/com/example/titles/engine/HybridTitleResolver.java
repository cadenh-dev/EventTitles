package com.example.titles.engine;

import com.example.titles.api.AwardedTitle;
import com.example.titles.api.EventContext;
import com.example.titles.api.LlmTitleGenerator;
import com.example.titles.api.TitleResolverService;
import com.example.titles.api.TitleText;
import com.example.titles.api.TitlesSettings;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class HybridTitleResolver implements TitleResolverService {
    private final StaticTitleRegistry registry;
    private final LlmTitleGenerator llm;
    private final Supplier<TitlesSettings> settings;
    public HybridTitleResolver(StaticTitleRegistry registry,LlmTitleGenerator llm,Supplier<TitlesSettings> settings){this.registry=registry;this.llm=llm;this.settings=settings;}

    @Override public CompletableFuture<Optional<AwardedTitle>> resolve(EventContext context) {
        Optional<AwardedTitle> stat;
        try { stat=registry.findStaticMatch(context); } catch(Throwable t) { stat=Optional.empty(); }
        if(stat.isPresent()&&stat.get().priority()>=90)return CompletableFuture.completedFuture(stat);
        final Optional<AwardedTitle> match=stat;
        TitlesSettings config;
        try { config=settings.get(); } catch(Throwable t) { return CompletableFuture.completedFuture(match); }
        if(config.llmEnabled()) {
            CompletableFuture<Optional<AwardedTitle>> request;
            try { request=llm.generateTitle(context); if(request==null)throw new IllegalStateException("LLM returned null future"); }
            catch(Throwable t) { return CompletableFuture.completedFuture(fallback(match,config)); }
            int timeout=Math.max(0,config.llmTimeoutSeconds());
            return request.completeOnTimeout(Optional.empty(),timeout+1L,TimeUnit.SECONDS)
                .exceptionally(t->Optional.empty()).thenApply(result->{
                    Optional<AwardedTitle> safe=result==null?Optional.empty():result;
                    if(safe.isPresent()) {
                        AwardedTitle title=safe.get();
                        String text=TitleText.truncateVisible(title.miniMessageText(),config.maxTitleLength());
                        return Optional.of(new AwardedTitle(text,Math.max(title.priority(),match.map(AwardedTitle::priority).orElse(1)),true));
                    }
                    return fallback(match,config);
                }).exceptionally(t->fallback(match,config));
        }
        return CompletableFuture.completedFuture(fallback(match,config));
    }
    private static Optional<AwardedTitle> fallback(Optional<AwardedTitle> match,TitlesSettings config){
        if(match.isPresent())return match;
        if(config.defaultTitle()==null||config.defaultTitle().isBlank())return Optional.empty();
        String text=TitleText.truncateVisible(config.defaultTitle(),config.maxTitleLength());
        return text==null||text.isBlank()?Optional.empty():Optional.of(new AwardedTitle(text,5,false));
    }
}
