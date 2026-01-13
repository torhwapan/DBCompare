-- ============================================
-- Oracle 和 PostgreSQL 数据对比 SQL 示例
-- ============================================

-- 方式一：使用 UNION 找出差异记录
-- ============================================

-- 1. 找出仅在 Oracle 中存在的记录
SELECT id, name, email FROM oracle_schema.user_info
MINUS
SELECT id, name, email FROM postgres_schema.user_info;

-- PostgreSQL 等效语句（使用 EXCEPT）
SELECT id, name, email FROM user_info
EXCEPT
SELECT id, name, email FROM user_info@oracle_dblink;


-- 2. 找出仅在 PostgreSQL 中存在的记录
SELECT id, name, email FROM postgres_schema.user_info
EXCEPT
SELECT id, name, email FROM oracle_schema.user_info;


-- 方式二：使用 LEFT JOIN 找出差异
-- ============================================

-- 3. 找出主键不匹配的记录（Oracle 有，PostgreSQL 没有）
SELECT o.id
FROM oracle_schema.user_info o
LEFT JOIN postgres_schema.user_info p ON o.id = p.id
WHERE p.id IS NULL;


-- 4. 找出主键不匹配的记录（PostgreSQL 有，Oracle 没有）
SELECT p.id
FROM postgres_schema.user_info p
LEFT JOIN oracle_schema.user_info o ON p.id = o.id
WHERE o.id IS NULL;


-- 方式三：对比记录总数
-- ============================================

-- 5. 对比两个数据库的记录总数
SELECT 
    'Oracle' AS source,
    COUNT(*) AS record_count
FROM oracle_schema.user_info

UNION ALL

SELECT 
    'PostgreSQL' AS source,
    COUNT(*) AS record_count
FROM postgres_schema.user_info;


-- 方式四：对比字段值差异
-- ============================================

-- 6. 找出字段值不一致的记录（使用 FULL OUTER JOIN）
SELECT 
    COALESCE(o.id, p.id) AS id,
    o.name AS oracle_name,
    p.name AS postgres_name,
    o.email AS oracle_email,
    p.email AS postgres_email,
    o.status AS oracle_status,
    p.status AS postgres_status
FROM oracle_schema.user_info o
FULL OUTER JOIN postgres_schema.user_info p ON o.id = p.id
WHERE 
    o.name != p.name OR
    o.email != p.email OR
    o.status != p.status OR
    o.id IS NULL OR
    p.id IS NULL;


-- 7. 更详细的字段值对比（带条件判断）
SELECT 
    o.id,
    CASE 
        WHEN o.name != p.name THEN 'name字段不一致'
        WHEN o.email != p.email THEN 'email字段不一致'
        WHEN o.status != p.status THEN 'status字段不一致'
        ELSE '数据一致'
    END AS diff_type,
    o.name AS oracle_name,
    p.name AS postgres_name,
    o.email AS oracle_email,
    p.email AS postgres_email
FROM oracle_schema.user_info o
INNER JOIN postgres_schema.user_info p ON o.id = p.id
WHERE o.name != p.name OR o.email != p.email OR o.status != p.status;


-- 方式五：使用 CHECKSUM/HASH 对比
-- ============================================

-- 8. Oracle - 使用 STANDARD_HASH 计算行哈希
SELECT 
    id,
    STANDARD_HASH(
        id || '|' || 
        NVL(name, 'NULL') || '|' || 
        NVL(email, 'NULL') || '|' || 
        NVL(TO_CHAR(status), 'NULL')
    ) AS row_hash
FROM oracle_schema.user_info;


-- 9. PostgreSQL - 使用 MD5 计算行哈希
SELECT 
    id,
    MD5(
        CAST(id AS TEXT) || '|' || 
        COALESCE(name, 'NULL') || '|' || 
        COALESCE(email, 'NULL') || '|' || 
        COALESCE(CAST(status AS TEXT), 'NULL')
    ) AS row_hash
FROM postgres_schema.user_info;


-- 10. 对比哈希值找出差异
WITH oracle_hash AS (
    SELECT 
        id,
        STANDARD_HASH(id || '|' || NVL(name, 'NULL') || '|' || NVL(email, 'NULL')) AS row_hash
    FROM oracle_schema.user_info
),
postgres_hash AS (
    SELECT 
        id,
        MD5(CAST(id AS TEXT) || '|' || COALESCE(name, 'NULL') || '|' || COALESCE(email, 'NULL')) AS row_hash
    FROM postgres_schema.user_info
)
SELECT 
    COALESCE(o.id, p.id) AS id,
    o.row_hash AS oracle_hash,
    p.row_hash AS postgres_hash
FROM oracle_hash o
FULL OUTER JOIN postgres_hash p ON o.id = p.id
WHERE o.row_hash != p.row_hash OR o.id IS NULL OR p.id IS NULL;


-- 方式六：批量对比多个表
-- ============================================

-- 11. 创建对比摘要（所有表的记录数对比）
SELECT 
    'user_info' AS table_name,
    (SELECT COUNT(*) FROM oracle_schema.user_info) AS oracle_count,
    (SELECT COUNT(*) FROM postgres_schema.user_info) AS postgres_count,
    (SELECT COUNT(*) FROM oracle_schema.user_info) - 
    (SELECT COUNT(*) FROM postgres_schema.user_info) AS diff

UNION ALL

SELECT 
    'order_info' AS table_name,
    (SELECT COUNT(*) FROM oracle_schema.order_info) AS oracle_count,
    (SELECT COUNT(*) FROM postgres_schema.order_info) AS postgres_count,
    (SELECT COUNT(*) FROM oracle_schema.order_info) - 
    (SELECT COUNT(*) FROM postgres_schema.order_info) AS diff

UNION ALL

SELECT 
    'product_info' AS table_name,
    (SELECT COUNT(*) FROM oracle_schema.product_info) AS oracle_count,
    (SELECT COUNT(*) FROM postgres_schema.product_info) AS postgres_count,
    (SELECT COUNT(*) FROM oracle_schema.product_info) - 
    (SELECT COUNT(*) FROM postgres_schema.product_info) AS diff;


-- 方式七：使用数据库链接（Database Link）
-- ============================================

-- 12. Oracle 通过 Database Link 查询 PostgreSQL（需要先创建 DB Link）
-- 创建 Database Link（需要管理员权限）
-- CREATE DATABASE LINK postgres_link
--   CONNECT TO postgres_user IDENTIFIED BY postgres_password
--   USING '(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=postgres_host)(PORT=5432))(CONNECT_DATA=(SERVICE_NAME=postgres_db)))';

-- 使用 Database Link 对比
SELECT o.id
FROM user_info o
LEFT JOIN user_info@postgres_link p ON o.id = p.id
WHERE p.id IS NULL;


-- 方式八：生成差异报告
-- ============================================

-- 13. 完整的差异报告（Oracle）
SELECT 
    '总记录数对比' AS report_type,
    TO_CHAR((SELECT COUNT(*) FROM oracle_schema.user_info)) AS oracle_value,
    TO_CHAR((SELECT COUNT(*) FROM postgres_schema.user_info)) AS postgres_value
FROM DUAL

UNION ALL

SELECT 
    '仅在Oracle中存在',
    TO_CHAR(COUNT(*)),
    'N/A'
FROM (
    SELECT id FROM oracle_schema.user_info
    MINUS
    SELECT id FROM postgres_schema.user_info
)

UNION ALL

SELECT 
    '仅在PostgreSQL中存在',
    'N/A',
    TO_CHAR(COUNT(*))
FROM (
    SELECT id FROM postgres_schema.user_info
    MINUS
    SELECT id FROM oracle_schema.user_info
)

UNION ALL

SELECT 
    '字段值不一致记录数',
    TO_CHAR(COUNT(*)),
    TO_CHAR(COUNT(*))
FROM oracle_schema.user_info o
INNER JOIN postgres_schema.user_info p ON o.id = p.id
WHERE o.name != p.name OR o.email != p.email;


-- 方式九：按时间范围对比
-- ============================================

-- 14. 对比最近一天的数据
SELECT 
    'Oracle' AS source,
    COUNT(*) AS new_records
FROM oracle_schema.user_info
WHERE created_at >= SYSDATE - 1

UNION ALL

SELECT 
    'PostgreSQL' AS source,
    COUNT(*) AS new_records
FROM postgres_schema.user_info
WHERE created_at >= CURRENT_DATE - INTERVAL '1 day';


-- 方式十：导出差异数据
-- ============================================

-- 15. 创建差异表（用于存储对比结果）
CREATE TABLE data_comparison_result (
    table_name VARCHAR2(100),
    primary_key VARCHAR2(100),
    field_name VARCHAR2(100),
    oracle_value VARCHAR2(4000),
    postgres_value VARCHAR2(4000),
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 16. 插入差异数据
INSERT INTO data_comparison_result (table_name, primary_key, field_name, oracle_value, postgres_value)
SELECT 
    'user_info' AS table_name,
    TO_CHAR(o.id) AS primary_key,
    'name' AS field_name,
    o.name AS oracle_value,
    p.name AS postgres_value
FROM oracle_schema.user_info o
INNER JOIN postgres_schema.user_info p ON o.id = p.id
WHERE o.name != p.name

UNION ALL

SELECT 
    'user_info',
    TO_CHAR(o.id),
    'email',
    o.email,
    p.email
FROM oracle_schema.user_info o
INNER JOIN postgres_schema.user_info p ON o.id = p.id
WHERE o.email != p.email;
