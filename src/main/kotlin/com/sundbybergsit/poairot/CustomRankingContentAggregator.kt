package com.sundbybergsit.poairot

import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.aggregator.ContentAggregator
import dev.langchain4j.rag.query.Query
import org.slf4j.LoggerFactory

// TODO: Implement (properly): https://github.com/Sundbybergs-IT/Po-AI-rot/issues/1
class CustomRankingContentAggregator(private val scoringModel: ScoringModel, private val topN: Int) :
    ContentAggregator {

    private val logger = LoggerFactory.getLogger(CustomRankingContentAggregator::class.java)

    override fun aggregate(p0: MutableMap<Query, MutableCollection<MutableList<Content>>>?): MutableList<Content> {
        var score = 0.0
        val content = mutableListOf<Content>()
        p0?.let { queryContentEntries ->
            for (entry in queryContentEntries) {
                for (text in entry.value.flatten()) {
                    val result = scoringModel.score(text.textSegment(), entry.key.text())
                    score += result.content()
                    content += Content.from(text.textSegment())
                }
            }
        }
        logger.info("Score is: {}", score)
        return if (content.size < topN) content.subList(0, topN) else content
    }

}
