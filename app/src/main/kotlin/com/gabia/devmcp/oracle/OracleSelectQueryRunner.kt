package com.gabia.devmcp.oracle

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

/**
 * 오라클 데이터베이스에서 SELECT 쿼리를 실행하고 결과를 반환하는 클래스
 */
class OracleSelectQueryRunner {
    
    companion object {
        private const val ORACLE_JDBC_URL_PREFIX = "jdbc:oracle:thin:@"
    }
    
    /**
     * 환경변수에서 오라클 연결 정보를 읽어오는 데이터 클래스
     */
    data class OracleConnectionConfig(
        val host: String,
        val port: String,
        val sid: String,
        val username: String,
        val password: String
    ) {
        val jdbcUrl: String
            get() = "$ORACLE_JDBC_URL_PREFIX$host:$port:$sid"
    }
    
    /**
     * 쿼리 실행 결과를 담는 데이터 클래스
     */
    data class QueryResult(
        val columns: List<String>,
        val rows: List<Map<String, Any?>>,
        val rowCount: Int
    )
    
    /**
     * 환경변수에서 오라클 연결 정보를 읽어옵니다.
     * 필요한 환경변수:
     * - ORACLE_HOST: 오라클 서버 호스트
     * - ORACLE_PORT: 오라클 서버 포트 (기본값: 1521)
     * - ORACLE_SID: 오라클 SID (기본값: DEVGABIA)
     * - ORACLE_USERNAME: 사용자명
     * - ORACLE_PASSWORD: 비밀번호
     */
    private fun getConnectionConfig(): OracleConnectionConfig {
        val host = System.getenv("ORACLE_HOST") 
            ?: throw IllegalStateException("ORACLE_HOST 환경변수가 설정되지 않았습니다.")
        
        val port = System.getenv("ORACLE_PORT") ?: "1521"
        val sid = System.getenv("ORACLE_SID") ?: "DEVGABIA"
        
        val username = System.getenv("ORACLE_USERNAME")
            ?: throw IllegalStateException("ORACLE_USERNAME 환경변수가 설정되지 않았습니다.")
        
        val password = System.getenv("ORACLE_PASSWORD")
            ?: throw IllegalStateException("ORACLE_PASSWORD 환경변수가 설정되지 않았습니다.")
        
        return OracleConnectionConfig(host, port, sid, username, password)
    }
    
    /**
     * 오라클 데이터베이스에 연결합니다.
     */
    private fun createConnection(): Connection {
        val config = getConnectionConfig()
        
        return try {
            DriverManager.getConnection(config.jdbcUrl, config.username, config.password)
        } catch (e: Exception) {
            throw RuntimeException("오라클 데이터베이스 연결에 실패했습니다: ${e.message}", e)
        }
    }
    
    /**
     * SELECT 쿼리를 실행하고 결과를 반환합니다.
     * 
     * @param query 실행할 SELECT 쿼리
     * @return QueryResult 쿼리 실행 결과
     * @throws IllegalArgumentException SELECT가 아닌 쿼리가 전달된 경우
     * @throws RuntimeException 데이터베이스 연결 또는 쿼리 실행 중 오류 발생 시
     */
    fun executeSelectQuery(query: String): QueryResult {
        // 쿼리 전처리: 앞뒤 공백 제거 및 끝의 세미콜론 제거
        val processedQuery = query.trim().let { trimmed ->
            if (trimmed.endsWith(";")) {
                trimmed.dropLast(1).trim()
            } else {
                trimmed
            }
        }
        
        // 쿼리 검증
        if (!processedQuery.uppercase().startsWith("SELECT")) {
            throw IllegalArgumentException("SELECT 쿼리만 실행할 수 있습니다. 입력된 쿼리: $query")
        }
        
        var connection: Connection? = null
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        
        try {
            connection = createConnection()
            statement = connection.createStatement()
            resultSet = statement.executeQuery(processedQuery)
            
            val metaData = resultSet.metaData
            val columnCount = metaData.columnCount
            
            // 컬럼명 추출
            val columns = (1..columnCount).map { i ->
                metaData.getColumnLabel(i)
            }
            
            // 결과 데이터 추출
            val rows = mutableListOf<Map<String, Any?>>()
            while (resultSet.next()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 1..columnCount) {
                    val columnName = metaData.getColumnLabel(i)
                    val value = resultSet.getObject(i)
                    row[columnName] = value
                }
                rows.add(row)
            }
            
            return QueryResult(
                columns = columns,
                rows = rows,
                rowCount = rows.size
            )
            
        } catch (e: Exception) {
            throw RuntimeException("쿼리 실행 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 리소스 정리
            resultSet?.close()
            statement?.close()
            connection?.close()
        }
    }
    
    /**
     * 연결 테스트를 수행합니다.
     * 
     * @return 연결 성공 여부
     */
    fun testConnection(): Boolean {
        return try {
            val connection = createConnection()
            connection.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
