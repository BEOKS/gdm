package com.gabia.devmcp.figma

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Figma 관련 MCP 도구 등록 클래스
 */
class FigmaTools {
    private val api = FigmaApiClient()

    fun addToServer(server: Server) {
        addGetFigmaDataTool(server)
        addDownloadFigmaImagesTool(server)
    }

    private fun addGetFigmaDataTool(server: Server) {
        server.addTool(
            name = "get_figma_data",
            description = "Get comprehensive Figma file data including layout, content, visuals, and component information",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("fileKey") {
                        put("type", "string")
                        put("description", "The key of the Figma file to fetch, from figma.com/(file|design)/<fileKey>/...")
                    }
                    putJsonObject("nodeId") {
                        put("type", "string")
                        put("description", "The ID of the node to fetch, use if provided")
                    }
                    putJsonObject("depth") {
                        put("type", "number")
                        put("description", "OPTIONAL. Do not use unless explicitly requested by the user.")
                    }
                },
                required = listOf("fileKey")
            )
        ) { req ->
            val fileKey = req.arguments["fileKey"]?.jsonPrimitive?.content
            val nodeId = req.arguments["nodeId"]?.jsonPrimitive?.contentOrNull
            val depth = req.arguments["depth"]?.jsonPrimitive?.intOrNull

            if (fileKey.isNullOrBlank()) {
                return@addTool CallToolResult(content = listOf(TextContent("fileKey is required")), isError = true)
            }

            return@addTool try {
                val raw = if (!nodeId.isNullOrBlank()) api.getNodes(fileKey, listOf(nodeId), depth) else api.getFile(fileKey, depth)

                val simplifiedNodes = extractNodes(raw)
                val nameVal = raw["name"]?.jsonPrimitive?.contentOrNull
                val lastModVal = raw["lastModified"]?.jsonPrimitive?.contentOrNull
                val versionVal = raw["version"]?.jsonPrimitive?.contentOrNull
                val meta = buildJsonObject {
                    put("name", JsonPrimitive(nameVal ?: if (!nodeId.isNullOrBlank()) "Node Data" else "Unknown"))
                    put("lastModified", JsonPrimitive(lastModVal ?: ""))
                    put("version", JsonPrimitive(versionVal ?: ""))
                }

                val result = buildJsonObject {
                    put("metadata", meta)
                    put("nodes", simplifiedNodes)
                    put("globalVars", buildJsonObject {
                        put("styles", buildJsonObject { })
                        put("components", buildJsonObject { })
                    })
                }

                CallToolResult(content = listOf(TextContent(Json.encodeToString(result))))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Error fetching Figma data: ${e.message}")), isError = true)
            }
        }
    }

    private fun addDownloadFigmaImagesTool(server: Server) {
        server.addTool(
            name = "download_figma_images",
            description = "Download SVG and PNG images used in a Figma file based on the IDs of image or icon nodes",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("fileKey") { put("type", "string"); put("description", "Figma file key (alphanumeric)") }
                    putJsonObject("nodes") {
                        put("type", "array")
                        put("description", "The nodes to fetch as images")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                putJsonObject("nodeId") { put("type", "string"); put("description", "Node ID like 1234:5678 or I5666:180910;1:10515;1:10336") }
                                putJsonObject("imageRef") { put("type", "string"); put("description", "imageRef if using fill image") }
                                putJsonObject("fileName") { put("type", "string"); put("description", "Local filename with extension") }
                                putJsonObject("needsCropping") { put("type", "boolean") }
                                putJsonObject("cropTransform") { put("type", "array") }
                                putJsonObject("requiresImageDimensions") { put("type", "boolean") }
                                putJsonObject("filenameSuffix") { put("type", "string") }
                            })
                            put("required", buildJsonArray { add("fileName") })
                        })
                    }
                    putJsonObject("pngScale") { put("type", "number"); put("description", "PNG export scale (default 2)") }
                    putJsonObject("localPath") { put("type", "string"); put("description", "Absolute target directory path") }
                },
                required = listOf("fileKey", "nodes", "localPath")
            )
        ) { req ->
            val fileKey = req.arguments["fileKey"]?.jsonPrimitive?.content
            val nodesArr = req.arguments["nodes"] as? JsonArray
            val pngScale = req.arguments["pngScale"]?.jsonPrimitive?.intOrNull ?: 2
            val localPath = req.arguments["localPath"]?.jsonPrimitive?.content

            if (fileKey.isNullOrBlank() || nodesArr == null || localPath.isNullOrBlank()) {
                return@addTool CallToolResult(content = listOf(TextContent("fileKey, nodes, localPath are required")), isError = true)
            }

            // Sanitize and ensure path within cwd
            val target = Path.of(localPath).normalize()
            val cwd = Path.of("").toAbsolutePath().normalize()
            if (!target.toAbsolutePath().startsWith(cwd)) {
                return@addTool CallToolResult(content = listOf(TextContent("Invalid path specified. Directory traversal is not allowed.")), isError = true)
            }
            if (!Files.exists(target)) Files.createDirectories(target)

            data class Item(
                val fileName: String,
                val imageRef: String?,
                val nodeId: String?,
            )

            val items = nodesArr.mapNotNull { el ->
                val obj = el.jsonObject
                val baseName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val suffix = obj["filenameSuffix"]?.jsonPrimitive?.contentOrNull
                val finalName = if (!suffix.isNullOrBlank() && !baseName.contains(suffix)) {
                    val idx = baseName.lastIndexOf('.')
                    if (idx > 0) baseName.substring(0, idx) + "-" + suffix + baseName.substring(idx) else baseName + "-" + suffix
                } else baseName
                Item(
                    fileName = finalName,
                    imageRef = obj["imageRef"]?.jsonPrimitive?.contentOrNull,
                    nodeId = obj["nodeId"]?.jsonPrimitive?.contentOrNull,
                )
            }

            return@addTool try {
                // 1) Resolve image fill URLs
                val fills = api.getImageFills(fileKey)
                val fillMap = fills["meta"]?.jsonObject?.get("images")?.jsonObject ?: buildJsonObject { }

                // 2) Split to fills and renders
                val fillItems = items.filter { it.imageRef != null }
                val renderItems = items.filter { it.nodeId != null }

                val downloaded = mutableListOf<String>()

                // 3) Download fills
                for (it in fillItems) {
                    val url = it.imageRef?.let { ref -> fillMap[ref]?.jsonPrimitive?.contentOrNull }
                    if (url != null) {
                        val bytes = api.downloadToBytes(url)
                        val file = target.resolve(it.fileName)
                        Files.write(file, bytes)
                        downloaded.add("${it.fileName}")
                    }
                }

                // 4) Render PNGs
                val pngNodes = renderItems.filter { !it.fileName.lowercase().endsWith(".svg") }.mapNotNull { it.nodeId }
                if (pngNodes.isNotEmpty()) {
                    val pngResp = api.getImages(fileKey, pngNodes, "png", mapOf("scale" to pngScale.toString()))
                    val images = pngResp["images"]?.jsonObject ?: buildJsonObject { }
                    for (it in renderItems.filter { r -> r.nodeId in pngNodes }) {
                        val url = it.nodeId?.let { id -> images[id]?.jsonPrimitive?.contentOrNull }
                        if (url != null) {
                            val bytes = api.downloadToBytes(url)
                            val file = target.resolve(it.fileName)
                            Files.write(file, bytes)
                            downloaded.add("${it.fileName}")
                        }
                    }
                }

                // 5) Render SVGs
                val svgNodes = renderItems.filter { it.fileName.lowercase().endsWith(".svg") }.mapNotNull { it.nodeId }
                if (svgNodes.isNotEmpty()) {
                    val svgResp = api.getImages(
                        fileKey,
                        svgNodes,
                        "svg",
                        mapOf(
                            "svg_outline_text" to "true",
                            "svg_include_id" to "false",
                            "svg_simplify_stroke" to "true"
                        )
                    )
                    val images = svgResp["images"]?.jsonObject ?: buildJsonObject { }
                    for (it in renderItems.filter { r -> r.nodeId in svgNodes }) {
                        val url = it.nodeId?.let { id -> images[id]?.jsonPrimitive?.contentOrNull }
                        if (url != null) {
                            val bytes = api.downloadToBytes(url)
                            val file = target.resolve(it.fileName)
                            Files.write(file, bytes)
                            downloaded.add("${it.fileName}")
                        }
                    }
                }

                val summary = buildString {
                    append("Downloaded ")
                    append(downloaded.size)
                    append(" images:\n")
                    append(downloaded.joinToString("\n") { "- $it" })
                }
                CallToolResult(content = listOf(TextContent(summary)))
            } catch (e: Exception) {
                CallToolResult(content = listOf(TextContent("Failed to download images: ${e.message}")), isError = true)
            }
        }
    }

    fun close() = api.close()
}

fun Server.addFigmaTools() {
    val tools = FigmaTools()
    tools.addToServer(this)
}

// ---------------------------
// Helpers to simplify nodes
// ---------------------------
private fun extractNodes(data: JsonObject): JsonElement {
    val document = data["document"]?.jsonObject
    if (document != null) {
        // Return a single root wrapped as array
        return JsonArray(listOf(walkNode(document)))
    }
    val nodesObj = data["nodes"]?.jsonObject
    if (nodesObj != null) {
        val arr = nodesObj.values.map { value ->
            val node = value.jsonObject
            val doc = node["document"]?.jsonObject ?: node
            walkNode(doc)
        }
        return JsonArray(arr)
    }
    return JsonArray(emptyList())
}

private fun walkNode(node: JsonObject): JsonObject {
    val children = node["children"] as? JsonArray
    val childNodes = children?.mapNotNull { el -> el.jsonObject }?.map { walkNode(it) }
    return buildJsonObject {
        put("id", node["id"] ?: JsonNull)
        put("name", node["name"] ?: JsonNull)
        put("type", node["type"] ?: JsonNull)
        val visible = node["visible"]?.jsonPrimitive?.booleanOrNull ?: true
        put("visible", JsonPrimitive(visible))
        if (childNodes != null && childNodes.isNotEmpty()) put("children", JsonArray(childNodes))
    }
}
