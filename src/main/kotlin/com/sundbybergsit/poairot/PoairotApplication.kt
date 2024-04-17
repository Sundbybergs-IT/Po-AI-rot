package com.sundbybergsit.poairot

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap


@SpringBootApplication
class PoairotApplication

fun main(args: Array<String>) {
    runApplication<PoairotApplication>(*args)
    val envProperties = checkNotNull(loadProperties())
    val polymath: Polymath = createPolymath(
        envProperties.getProperty("organization_id"),
        envProperties.getProperty("openai_api_key"),
        envProperties.getProperty("weaviate_api_key"),
        envProperties.getProperty("weaviate_url")
    )
    println(
        polymath.answer(
            "Du är talesperson för polisens kalla fall-grupp och antar personan av Hercule Poirot, " +
                    "känd för sitt detaljerade och noggranna detektivarbete. " +
                    "Du använder en teatralisk ton liknande Poirots, men ditt språk är övervägande svenska. " +
                    "Du kan inkludera några sporadiska franska fraser för att förstärka karaktären, " +
                    "men huvuddelen av kommunikationen och all teknisk information om utredningen av mordet på Olof Palme ska vara på svenska. " +
                    "Kom ihåg att all information om fallet är offentlig och ska hanteras korrekt. När du svarar på frågan: '${args[0]}', " +
                    "analysera och tolka den med fokus på detta mordfall och använd relevanta detaljer och fakta för att ge ett trovärdigt och informativt svar."
        )
    )
}

fun loadProperties(): Properties? {
    val prop = Properties()
    val classLoader = Thread.currentThread().contextClassLoader
    classLoader.getResourceAsStream("env.properties").use { input ->
        if (input == null) {
            println("Sorry, unable to find env.properties")
            return null
        }

        prop.load(input)
        return prop
    }
}

private fun createPolymath(
    openAiOrganizationId: String,
    openAiApiKey: String,
    weaviateApiKey: String,
    weaviateUrl: String,
): Polymath {
    val chatModel: ChatLanguageModel = OpenAiChatModel.builder()
        .organizationId(openAiOrganizationId)
        .apiKey(openAiApiKey)
        .maxRetries(2)
        .modelName(OpenAiChatModelName.GPT_4)
        .build()

    val embeddingModel: EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    val weaviateEmbeddingStore = WeaviateEmbeddingStore.builder()
        .apiKey(weaviateApiKey)
        .scheme("https")
        .host(weaviateUrl)
        .build()

    val proMemoriaContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(proMemoriaEmbeddingStore(embeddingModel, weaviateEmbeddingStore))
        .embeddingModel(embeddingModel)
        .maxResults(30)
        .minScore(0.7)
        .build()

    val factsContentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embed(toPath("/mop/txt/facts.txt"), embeddingModel, weaviateEmbeddingStore))
        .embeddingModel(embeddingModel)
        .maxResults(10)
        .minScore(0.6)
        .build()

    val retrieverToDescription: MutableMap<ContentRetriever, String> = HashMap()

    for (protocol in getProtocols(
        Triple(
            "protokoll skrivet av Brigitta Brolund. Källa: pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen",
            "/mop/txt/protokoll/pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen.txt",
            "pol-1986-02-28-Anteckningar-Brigitta-Brolund-ledningscentralen"
        ),
        Triple(
            "Obduktionsprotokollet 1 mars 1986",
            "/mop/txt/protokoll/obduktionsprotokollet.txt",
            "D:nr F 719/86"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore
    )) {
        retrieverToDescription[protocol.second] =
            "polisförhör av ${protocol.first} med anledning av mordet på Olof Palme. Källa: ${protocol.third}"
    }

    for (hearing in getHearings(
        Triple(
            "Lars Jepsson 1 mars 1986",
            "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-01"
        ),
        Triple(
            "Christer Andersson 1 november 1994",
            "/mop/txt/forhor/pol-1994-01-11-forhor-christer-a.txt",
            "pol-1994-01-11-forhor-christer-a"
        ),
        Triple(
            "Annette Kohut 19 mars 1986",
            "/mop/txt/forhor/pol-1986-03-19-anette-kohut-forsta-forhor.txt",
            "pol-1986-03-19-anette-kohut-forsta-forhor"
        ),
        Triple(
            "Annette Kohut 26 juni 1986",
            "/mop/txt/forhor/pol-1986-06-26-annette-kohut-forhor.txt",
            "pol-1986-06-26-annette-kohut-forhor"
        ),
        Triple(
            "Lars Jeppsson 1 mars 1986",
            "/mop/txt/forhor/pol-E15-00-Lars-Jeppsson-1986-03-01.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-01"
        ),
        Triple(
            "Lars Jeppsson 4 mars 1986",
            "/mop/txt/forhor/pol-E15-00-A-Lars-Jeppsson-1986-03-04.txt",
            "pol-E15-00-Lars-Jeppsson-1986-03-04"
        ),
        Triple(
            "Anders Delsborn 1 mars 1986",
            "/mop/txt/forhor/VITTNESFÖRHÖR-Anders-Delsborn-1986-03-01.txt",
            "VITTNESFÖRHÖR-Anders-Delsborn-1986-03-01"
        ),
        Triple(
            "Anders Björkman 1 mars 1986",
            "/mop/txt/forhor/pol-E13-A-Björkman-Anders-1986-03-01.txt",
            "pol-E13-A-Björkman-Anders-1986-03-01"
        ),
        Triple(
            "Anders Björkman 25 mars 1987",
            "/mop/txt/forhor/pol-E13-C-Björkman-Anders-1987-03-25.txt",
            "pol-E13-C-Björkman-Anders-1987-03-25"
        ),
        Triple(
            "Inge Morelius 1 mars 1986",
            "/mop/txt/forhor/Pol-1986-03-01_0920_E107-00_Förhör_med_Inge_Morelius.txt",
            "Pol-1986-03-01_0920_E107-00_Förhör_med_Inge_Morelius"
        ),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore
    )) {
        retrieverToDescription[hearing.second] =
            "polisförhör av ${hearing.first} med anledning av mordet på Olof Palme. Källa: ${hearing.third}"
    }

    for (person in getPersons(
        Pair("mördaren", "/mop/txt/personer/mordaren.txt"),
        Pair("Christer Pettersson", "/mop/txt/personer/christer-pettersson.txt"),
        Pair("Christer Andersson", "/mop/txt/personer/christer-andersson.txt"),
        Pair("Anders Björkman", "/mop/txt/personer/anders-bjorkman.txt"),
        Pair("Lars Jeppsson", "/mop/txt/personer/lars-jeppsson.txt"),
        Pair("Anders Delsborn", "/mop/txt/personer/anders-delsborn.txt"),
        Pair("Inge Morelius", "/mop/txt/personer/inge-morelius.txt"),
        Pair("Olof Palme", "/mop/txt/personer/olof-palme.txt"),
        embeddingModel = embeddingModel,
        weaviateEmbeddingStore = weaviateEmbeddingStore
    )) {
        retrieverToDescription[person.second] = "Allmänna faktauppgifter om ${person.first}"
    }
    retrieverToDescription[factsContentRetriever] =
        "fakta om mordet på Olof Palme. Ingen särskild källa, allmänna uppgifter."
    retrieverToDescription[proMemoriaContentRetriever] =
        "polis-promemoria om mordet på Olof Palme. Källa: pol-1987-02-09-e-63-1-pm-uppfoljning-av-engstrom-o.txt"
    val queryRouter: QueryRouter = LanguageModelQueryRouter(chatModel, retrieverToDescription)

    val retrievalAugmentor: RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(queryRouter)
        .build()

    return AiServices.builder(Polymath::class.java)
        .chatLanguageModel(chatModel)
        .retrievalAugmentor(retrievalAugmentor)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(1))
        .build()
}

private fun proMemoriaEmbeddingStore(
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
) = embed(
    toPath("/mop/txt/pm/pol-1987-02-09-e-63-1-pm-uppfoljning-av-engstrom-o.txt"),
    embeddingModel,
    weaviateEmbeddingStore
)

fun getPersons(
    vararg titleFileNamePairs: Pair<String, String>,
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
): List<Pair<String, ContentRetriever>> {
    val result: MutableList<Pair<String, ContentRetriever>> = mutableListOf()
    for (titleFileNamePair in titleFileNamePairs) {
        result.add(
            Pair(
                titleFileNamePair.first,
                EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embed(toPath(titleFileNamePair.second), embeddingModel, weaviateEmbeddingStore))
                    .embeddingModel(embeddingModel)
                    .maxResults(25)
                    .minScore(0.6)
                    .build()
            )
        )
    }
    return result
}

fun getProtocols(
    vararg titleFileNameTriples: Triple<String, String, String>,
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
): List<Triple<String, ContentRetriever, String>> {
    val result: MutableList<Triple<String, ContentRetriever, String>> = mutableListOf()
    for (titleFileNameTriple in titleFileNameTriples) {
        val protocolEmbeddingStore = embed(toPath(titleFileNameTriple.second), embeddingModel, weaviateEmbeddingStore)
        result.add(
            Triple(
                titleFileNameTriple.first,
                EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(protocolEmbeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(40)
                    .minScore(0.7)
                    .build(), titleFileNameTriple.third
            )
        )
    }
    return result
}

fun getHearings(
    vararg titleFileNameTriples: Triple<String, String, String>,
    embeddingModel: EmbeddingModel,
    weaviateEmbeddingStore: WeaviateEmbeddingStore,
): List<Triple<String, ContentRetriever, String>> {
    val result: MutableList<Triple<String, ContentRetriever, String>> = mutableListOf()
    for (titleFileNameTriple in titleFileNameTriples) {
        val hearingEmbeddingStore = embed(toPath(titleFileNameTriple.second), embeddingModel, weaviateEmbeddingStore)
        result.add(
            Triple(
                titleFileNameTriple.first,
                EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(hearingEmbeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(30)
                    .minScore(0.7)
                    .build(), titleFileNameTriple.third
            )
        )
    }
    return result
}

private fun embed(
    documentPath: Path,
    embeddingModel: EmbeddingModel,
    embeddingStore: EmbeddingStore<TextSegment>
): EmbeddingStore<TextSegment> {
    val documentParser: DocumentParser = TextDocumentParser()
    val document: Document = FileSystemDocumentLoader.loadDocument(documentPath, documentParser)
    val splitter = DocumentSplitters.recursive(300, 0)
    val segments = splitter.split(document)
    val embeddings: List<Embedding> = embeddingModel.embedAll(segments).content()
    embeddingStore.addAll(embeddings, segments)
    return embeddingStore
}

private fun toPath(fileName: String): Path {
    try {
        val fileUrl: URL = PoairotApplication::class.java.getResource(fileName)!!
        return Paths.get(fileUrl.toURI())
    } catch (e: URISyntaxException) {
        throw RuntimeException(e)
    }
}

internal interface Polymath {

    fun answer(query: String?): String?

}