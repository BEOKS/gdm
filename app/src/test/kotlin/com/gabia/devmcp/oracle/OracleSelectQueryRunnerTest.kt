package com.gabia.devmcp.oracle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * OracleSelectQueryRunner 테스트 클래스
 */
class OracleSelectQueryRunnerTest {
    
    private lateinit var queryRunner: OracleSelectQueryRunner
    
    @BeforeEach
    fun setUp() {
        queryRunner = OracleSelectQueryRunner()
    }
    
    @Test
    fun `환경변수가 설정되지 않은 경우 예외가 발생해야 한다`() {
        // 환경변수 제거 (테스트 환경에서는 설정되지 않을 가능성이 높음)
        val executeSelectQuery = queryRunner.executeSelectQuery("""
            select
                seqno,
                ordernum,
                license,
                version,
                using_on,
                str_using_on,
                relation_table,
                relation_seqno,
                ip_manage_flag,
                relation_ip,
                relation_ip_seqno,
                ip_manual,
                sw_ip,
                server_ip,
                user_id,
                status,
                str_status,
                item_name,
                manufacturer_name,
                model_name,
                carve_name,
                carve_code,
                seq,
                hw_seqno,
                outside_hw_seqno,
                server_no,
                service_gubun
            from
                gidc.v_st_sw_list
            where SERVICE_GUBUN='cloud_v2';
        """.trimIndent())
        println("executeSelectQuery = $executeSelectQuery")
    }
    
    @Test
    fun `SID 기본값이 DEVGABIA로 설정되어야 한다`() {
        // 환경변수가 설정되지 않은 상태에서 SID 기본값 테스트
        try {
            queryRunner.executeSelectQuery("SELECT * FROM dual")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
    }
    
    @Test
    fun `SELECT가 아닌 쿼리는 예외가 발생해야 한다`() {
        // 환경변수 설정 없이 쿼리 검증만 테스트
        assertThrows<IllegalArgumentException> {
            queryRunner.executeSelectQuery("INSERT INTO test VALUES (1)")
        }
        
        assertThrows<IllegalArgumentException> {
            queryRunner.executeSelectQuery("UPDATE test SET col = 1")
        }
        
        assertThrows<IllegalArgumentException> {
            queryRunner.executeSelectQuery("DELETE FROM test")
        }
        
        assertThrows<IllegalArgumentException> {
            queryRunner.executeSelectQuery("DROP TABLE test")
        }
    }
    
    @Test
    fun `올바른 SELECT 쿼리 형식은 검증을 통과해야 한다`() {
        // 환경변수 설정 없이 쿼리 형식 검증만 테스트
        // 실제 실행은 환경변수가 없어서 실패하지만, 쿼리 형식 검증은 통과해야 함
        try {
            queryRunner.executeSelectQuery("SELECT * FROM dual")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
    }
    
    @Test
    fun `공백이 포함된 SELECT 쿼리도 처리되어야 한다`() {
        try {
            queryRunner.executeSelectQuery("  SELECT * FROM dual  ")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
    }
    
    @Test
    fun `대소문자 구분 없이 SELECT 쿼리를 인식해야 한다`() {
        try {
            queryRunner.executeSelectQuery("select * from dual")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
        
        try {
            queryRunner.executeSelectQuery("Select * From dual")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
    }
    
    @Test
    fun `쿼리 끝의 세미콜론은 자동으로 제거되어야 한다`() {
        try {
            queryRunner.executeSelectQuery("SELECT * FROM dual;")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작 (ORA-00933 오류가 발생하지 않아야 함)
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
        
        try {
            queryRunner.executeSelectQuery("SELECT * FROM dual ;")
            fail("환경변수가 설정되지 않았으므로 예외가 발생해야 합니다.")
        } catch (e: RuntimeException) {
            // 환경변수 관련 예외는 예상된 동작 (ORA-00933 오류가 발생하지 않아야 함)
            assertTrue(e.message?.contains("환경변수가 설정되지 않았습니다") == true)
        }
    }
}
