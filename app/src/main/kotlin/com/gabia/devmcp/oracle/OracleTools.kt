package com.gabia.devmcp.oracle

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*

/**
 * 오라클 데이터베이스 관련 MCP 도구들을 추가합니다.
 */
fun Server.addOracleTools() {
    addTool(
        name = "oracle_execute_select",
        description = "오라클 데이터베이스에서 SELECT 쿼리를 실행하고 결과를 반환합니다.",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "실행할 SELECT 쿼리문")
                }
            },
            required = listOf("query")
        )
    ) { request ->
        val query = request.arguments["query"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("query 파라미터가 필요합니다.")),
                isError = true
            )
        
        return@addTool try {
            val queryRunner = OracleSelectQueryRunner()
            val result = queryRunner.executeSelectQuery(query)
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        """
                        쿼리 실행 완료!
                        
                        컬럼: ${result.columns.joinToString(", ")}
                        행 수: ${result.rowCount}
                        
                        결과:
                        ${formatQueryResult(result)}
                        """.trimIndent()
                    )
                ),
                isError = false
            )
        } catch (e: IllegalArgumentException) {
            CallToolResult(
                content = listOf(TextContent("오류: ${e.message}")),
                isError = true
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("쿼리 실행 중 오류가 발생했습니다: ${e.message}")),
                isError = true
            )
        }
    }
    
    addTool(
        name = "oracle_test_connection",
        description = "오라클 데이터베이스 연결을 테스트합니다.",
        inputSchema = Tool.Input(
            properties = buildJsonObject { },
            required = emptyList()
        )
    ) { _ ->
        return@addTool try {
            val queryRunner = OracleSelectQueryRunner()
            val isConnected = queryRunner.testConnection()
            
            CallToolResult(
                content = listOf(
                    TextContent(
                        if (isConnected) {
                            "오라클 데이터베이스 연결 성공!"
                        } else {
                            "오라클 데이터베이스 연결 실패. 환경변수를 확인해주세요."
                        }
                    )
                ),
                isError = !isConnected
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("연결 테스트 중 오류가 발생했습니다: ${e.message}")),
                isError = true
            )
        }
    }
}

/**
 * 쿼리 결과를 보기 좋게 포맷팅합니다.
 */
private fun formatQueryResult(result: OracleSelectQueryRunner.QueryResult): String {
    if (result.rows.isEmpty()) {
        return "결과가 없습니다."
    }
    
    val sb = StringBuilder()
    
    // 컬럼 헤더 출력
    val columnWidths = result.columns.map { column ->
        maxOf(
            column.length,
            result.rows.maxOfOrNull { row ->
                row[column]?.toString()?.length ?: 0
            } ?: 0
        )
    }
    
    // 헤더 라인
    sb.appendLine("┌${columnWidths.joinToString("┬") { "─".repeat(it) }}┐")
    sb.appendLine("│${result.columns.mapIndexed { i, col -> col.padEnd(columnWidths[i]) }.joinToString("│")}│")
    sb.appendLine("├${columnWidths.joinToString("┼") { "─".repeat(it) }}┤")
    
    // 데이터 라인들
    result.rows.forEach { row ->
        val values = result.columns.mapIndexed { i, column ->
            val value = row[column]?.toString() ?: "NULL"
            value.padEnd(columnWidths[i])
        }
        sb.appendLine("│${values.joinToString("│")}│")
    }
    
    sb.appendLine("└${columnWidths.joinToString("┴") { "─".repeat(it) }}┘")
    
    return sb.toString()
}
