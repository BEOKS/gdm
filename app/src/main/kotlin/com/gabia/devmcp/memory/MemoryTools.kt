package com.gabia.devmcp.memory

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Serializable
data class Entity(
    val name: String,
    val entityType: String,
    val observations: MutableList<String>
)

@Serializable
data class Relation(
    val from: String,
    val to: String,
    val relationType: String
)

@Serializable
data class KnowledgeGraph(
    val entities: MutableList<Entity> = mutableListOf(),
    val relations: MutableList<Relation> = mutableListOf()
)

private fun resolveMemoryPath(): Path {
    val env = System.getenv("MEMORY_FILE_PATH")
    // Resolve base directory to "jar directory" when possible; fallback to CWD
    val baseDir = try {
        val uri = KnowledgeGraphManager::class.java.protectionDomain.codeSource.location.toURI()
        val p = Paths.get(uri)
        if (Files.isDirectory(p)) p else p.parent
    } catch (_: Exception) {
        Paths.get(".").toAbsolutePath().normalize()
    }

    return if (env.isNullOrBlank()) {
        baseDir.resolve("memory.json").normalize()
    } else {
        val provided = Paths.get(env)
        if (provided.isAbsolute) provided.normalize() else baseDir.resolve(provided).normalize()
    }
}

class KnowledgeGraphManager(
    private val memoryFile: Path = resolveMemoryPath()
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun loadGraph(): KnowledgeGraph {
        if (!memoryFile.exists()) return KnowledgeGraph()
        val lines = Files.readAllLines(memoryFile).filter { it.isNotBlank() }
        val graph = KnowledgeGraph()
        for (line in lines) {
            val el = try { json.parseToJsonElement(line) } catch (_: Exception) { continue }
            val obj = el.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "entity" -> {
                    val name = obj["name"]?.jsonPrimitive?.content ?: continue
                    val entityType = obj["entityType"]?.jsonPrimitive?.content ?: continue
                    val observations = obj["observations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toMutableList()
                        ?: mutableListOf()
                    graph.entities.add(Entity(name, entityType, observations))
                }
                "relation" -> {
                    val from = obj["from"]?.jsonPrimitive?.content ?: continue
                    val to = obj["to"]?.jsonPrimitive?.content ?: continue
                    val relationType = obj["relationType"]?.jsonPrimitive?.content ?: continue
                    graph.relations.add(Relation(from, to, relationType))
                }
            }
        }
        return graph
    }

    private suspend fun saveGraph(graph: KnowledgeGraph) {
        memoryFile.parent?.createDirectories()
        val lines = buildList {
            graph.entities.forEach { e ->
                add(
                    json.encodeToString(
                        buildJsonObject {
                            put("type", JsonPrimitive("entity"))
                            put("name", JsonPrimitive(e.name))
                            put("entityType", JsonPrimitive(e.entityType))
                            put("observations", JsonArray(e.observations.map { JsonPrimitive(it) }))
                        }
                    )
                )
            }
            graph.relations.forEach { r ->
                add(
                    json.encodeToString(
                        buildJsonObject {
                            put("type", JsonPrimitive("relation"))
                            put("from", JsonPrimitive(r.from))
                            put("to", JsonPrimitive(r.to))
                            put("relationType", JsonPrimitive(r.relationType))
                        }
                    )
                )
            }
        }
        Files.writeString(memoryFile, lines.joinToString("\n"))
    }

    suspend fun createEntities(entities: List<Entity>): List<Entity> = mutex.withLock {
        val graph = loadGraph()
        val newEntities = entities.filter { e -> graph.entities.none { it.name == e.name } }
        graph.entities.addAll(newEntities)
        saveGraph(graph)
        newEntities
    }

    suspend fun createRelations(relations: List<Relation>): List<Relation> = mutex.withLock {
        val graph = loadGraph()
        val newRelations = relations.filter { r ->
            graph.relations.none { it.from == r.from && it.to == r.to && it.relationType == r.relationType }
        }
        graph.relations.addAll(newRelations)
        saveGraph(graph)
        newRelations
    }

    suspend fun addObservations(observations: List<Pair<String, List<String>>>): List<Pair<String, List<String>>> = mutex.withLock {
        val graph = loadGraph()
        val results = mutableListOf<Pair<String, List<String>>>()
        for ((entityName, contents) in observations) {
            val entity = graph.entities.find { it.name == entityName }
                ?: throw IllegalArgumentException("Entity with name $entityName not found")
            val newOnes = contents.filter { it !in entity.observations }
            entity.observations.addAll(newOnes)
            results.add(entityName to newOnes)
        }
        saveGraph(graph)
        results
    }

    suspend fun deleteEntities(entityNames: List<String>) = mutex.withLock {
        val graph = loadGraph()
        graph.entities.removeIf { it.name in entityNames }
        graph.relations.removeIf { it.from in entityNames || it.to in entityNames }
        saveGraph(graph)
    }

    suspend fun deleteObservations(deletions: List<Pair<String, List<String>>>) = mutex.withLock {
        val graph = loadGraph()
        deletions.forEach { (entityName, obs) ->
            val entity = graph.entities.find { it.name == entityName } ?: return@forEach
            entity.observations.removeIf { it in obs }
        }
        saveGraph(graph)
    }

    suspend fun deleteRelations(relations: List<Relation>) = mutex.withLock {
        val graph = loadGraph()
        graph.relations.removeIf { r -> relations.any { it.from == r.from && it.to == r.to && it.relationType == r.relationType } }
        saveGraph(graph)
    }

    suspend fun readGraph(): KnowledgeGraph = mutex.withLock { loadGraph() }

    suspend fun searchNodes(query: String): KnowledgeGraph = mutex.withLock {
        val graph = loadGraph()
        val q = query.lowercase()
        val filteredEntities = graph.entities.filter { e ->
            e.name.lowercase().contains(q) ||
                    e.entityType.lowercase().contains(q) ||
                    e.observations.any { it.lowercase().contains(q) }
        }
        val names = filteredEntities.map { it.name }.toSet()
        val filteredRelations = graph.relations.filter { it.from in names && it.to in names }
        KnowledgeGraph(filteredEntities.toMutableList(), filteredRelations.toMutableList())
    }

    suspend fun openNodes(names: List<String>): KnowledgeGraph = mutex.withLock {
        val graph = loadGraph()
        val filteredEntities = graph.entities.filter { it.name in names }
        val set = filteredEntities.map { it.name }.toSet()
        val filteredRelations = graph.relations.filter { it.from in set && it.to in set }
        KnowledgeGraph(filteredEntities.toMutableList(), filteredRelations.toMutableList())
    }
}

class MemoryTools(private val manager: KnowledgeGraphManager = KnowledgeGraphManager()) {
    fun addToServer(server: Server) {
        addCreateEntities(server)
        addCreateRelations(server)
        addAddObservations(server)
        addDeleteEntities(server)
        addDeleteObservations(server)
        addDeleteRelations(server)
        addReadGraph(server)
        addSearchNodes(server)
        addOpenNodes(server)
    }

    private fun addCreateEntities(server: Server) {
        server.addTool(
            name = "create_entities",
            description = "Create multiple new entities in the knowledge graph",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("entities", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity")) })
                                put("entityType", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The type of the entity")) })
                                put("observations", buildJsonObject {
                                    put("type", JsonPrimitive("array"))
                                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                                    put("description", JsonPrimitive("An array of observation contents associated with the entity"))
                                })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("entityType"), JsonPrimitive("observations"))))
                            put("additionalProperties", JsonPrimitive(false))
                        })
                    })
                },
                required = listOf("entities")
            )
        ) { req ->
            val array = req.arguments["entities"]?.jsonArray ?: return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'entities'")), isError = true
            )
            val entities = array.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    Entity(
                        name = obj["name"]!!.jsonPrimitive.content,
                        entityType = obj["entityType"]!!.jsonPrimitive.content,
                        observations = obj["observations"]!!.jsonArray.map { it.jsonPrimitive.content }.toMutableList()
                    )
                }.getOrNull()
            }
            return@addTool try {
                val created = manager.createEntities(entities)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(created))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addCreateRelations(server: Server) {
        server.addTool(
            name = "create_relations",
            description = "Create multiple new relations between entities in the knowledge graph. Relations should be in active voice",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("relations", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("from", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity where the relation starts")) })
                                put("to", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity where the relation ends")) })
                                put("relationType", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The type of the relation")) })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("from"), JsonPrimitive("to"), JsonPrimitive("relationType"))))
                            put("additionalProperties", JsonPrimitive(false))
                        })
                    })
                },
                required = listOf("relations")
            )
        ) { req ->
            val array = req.arguments["relations"]?.jsonArray ?: return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'relations'")), isError = true
            )
            val rels = array.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    Relation(
                        from = obj["from"]!!.jsonPrimitive.content,
                        to = obj["to"]!!.jsonPrimitive.content,
                        relationType = obj["relationType"]!!.jsonPrimitive.content
                    )
                }.getOrNull()
            }
            return@addTool try {
                val created = manager.createRelations(rels)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(created))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addAddObservations(server: Server) {
        server.addTool(
            name = "add_observations",
            description = "Add new observations to existing entities in the knowledge graph",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("observations", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("entityName", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity to add the observations to")) })
                                put("contents", buildJsonObject {
                                    put("type", JsonPrimitive("array"))
                                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                                    put("description", JsonPrimitive("An array of observation contents to add"))
                                })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("entityName"), JsonPrimitive("contents"))))
                            put("additionalProperties", JsonPrimitive(false))
                        })
                    })
                },
                required = listOf("observations")
            )
        ) { req ->
            val array = req.arguments["observations"]?.jsonArray ?: return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'observations'")), isError = true
            )
            val pairs = array.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    val entityName = obj["entityName"]!!.jsonPrimitive.content
                    val contents = obj["contents"]!!.jsonArray.map { it.jsonPrimitive.content }
                    entityName to contents
                }.getOrNull()
            }
            return@addTool try {
                val result = manager.addObservations(pairs)
                // Return shape: { entityName, addedObservations }
                val arr = JsonArray(result.map { (name, added) ->
                    buildJsonObject {
                        put("entityName", JsonPrimitive(name))
                        put("addedObservations", JsonArray(added.map { JsonPrimitive(it) }))
                    }
                })
                CallToolResult(content = listOf(TextContent(Json.encodeToString(arr))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDeleteEntities(server: Server) {
        server.addTool(
            name = "delete_entities",
            description = "Delete multiple entities and their associated relations from the knowledge graph",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("entityNames", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                        put("description", JsonPrimitive("An array of entity names to delete"))
                    })
                },
                required = listOf("entityNames")
            )
        ) { req ->
            val names = req.arguments["entityNames"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            return@addTool try {
                manager.deleteEntities(names)
                CallToolResult(content = listOf(TextContent("Entities deleted successfully")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDeleteObservations(server: Server) {
        server.addTool(
            name = "delete_observations",
            description = "Delete specific observations from entities in the knowledge graph",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("deletions", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("entityName", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity containing the observations")) })
                                put("observations", buildJsonObject {
                                    put("type", JsonPrimitive("array"))
                                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                                    put("description", JsonPrimitive("An array of observations to delete"))
                                })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("entityName"), JsonPrimitive("observations"))))
                            put("additionalProperties", JsonPrimitive(false))
                        })
                    })
                },
                required = listOf("deletions")
            )
        ) { req ->
            val array = req.arguments["deletions"]?.jsonArray ?: return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'deletions'")), isError = true
            )
            val pairs = array.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    val entityName = obj["entityName"]!!.jsonPrimitive.content
                    val obs = obj["observations"]!!.jsonArray.map { it.jsonPrimitive.content }
                    entityName to obs
                }.getOrNull()
            }
            return@addTool try {
                manager.deleteObservations(pairs)
                CallToolResult(content = listOf(TextContent("Observations deleted successfully")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDeleteRelations(server: Server) {
        server.addTool(
            name = "delete_relations",
            description = "Delete multiple relations from the knowledge graph",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("relations", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("from", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity where the relation starts")) })
                                put("to", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The name of the entity where the relation ends")) })
                                put("relationType", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The type of the relation")) })
                            })
                            put("required", JsonArray(listOf(JsonPrimitive("from"), JsonPrimitive("to"), JsonPrimitive("relationType"))))
                            put("additionalProperties", JsonPrimitive(false))
                        })
                    })
                },
                required = listOf("relations")
            )
        ) { req ->
            val array = req.arguments["relations"]?.jsonArray ?: return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'relations'")), isError = true
            )
            val rels = array.mapNotNull { el ->
                runCatching {
                    val obj = el.jsonObject
                    Relation(
                        from = obj["from"]!!.jsonPrimitive.content,
                        to = obj["to"]!!.jsonPrimitive.content,
                        relationType = obj["relationType"]!!.jsonPrimitive.content
                    )
                }.getOrNull()
            }
            return@addTool try {
                manager.deleteRelations(rels)
                CallToolResult(content = listOf(TextContent("Relations deleted successfully")))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addReadGraph(server: Server) {
        server.addTool(
            name = "read_graph",
            description = "Read the entire knowledge graph",
            inputSchema = Tool.Input(properties = buildJsonObject { }, required = emptyList())
        ) { _ ->
            return@addTool try {
                val graph = manager.readGraph()
                CallToolResult(content = listOf(TextContent(Json.encodeToString(graph))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addSearchNodes(server: Server) {
        server.addTool(
            name = "search_nodes",
            description = "Search for nodes in the knowledge graph based on a query",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("query", buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The search query to match against entity names, types, and observation content")) })
                },
                required = listOf("query")
            )
        ) { req ->
            val query = req.arguments["query"]?.jsonPrimitive?.content
            if (query.isNullOrBlank()) return@addTool CallToolResult(
                content = listOf(TextContent("Missing 'query'")), isError = true
            )
            return@addTool try {
                val result = manager.searchNodes(query)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }

    private fun addOpenNodes(server: Server) {
        server.addTool(
            name = "open_nodes",
            description = "Open specific nodes in the knowledge graph by their names",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    put("names", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                        put("description", JsonPrimitive("An array of entity names to retrieve"))
                    })
                },
                required = listOf("names")
            )
        ) { req ->
            val names = req.arguments["names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            return@addTool try {
                val result = manager.openNodes(names)
                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error: ${e.message}")), isError = true)
            }
        }
    }
}

fun Server.addMemoryTools() {
    val tools = MemoryTools()
    tools.addToServer(this)
}
