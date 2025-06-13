import java
import semmle.code.java.dataflow.DataFlow

predicate containsSql(StringLiteral lit) {
  exists(string val | val = lit.getValue().toLowerCase() |
    // Standard SQL keywords
    val.matches("%select%") or val.matches("%insert%") or val.matches("%update%") or
    val.matches("%delete%") or val.matches("%create%") or val.matches("%alter%") or
    val.matches("%drop%") or val.matches("%from%") or val.matches("%where%") or
    val.matches("%join%") or val.matches("%group by%") or val.matches("%order by%") or
    // DB2 specific patterns
    val.matches("%sysibm%") or val.matches("%syscat%") or val.matches("%sysstat%") or
    val.matches("%fetch first%") or val.matches("%rows only%") or
    val.matches("%with ur%") or val.matches("%with cs%") or val.matches("%with rs%") or
    val.matches("%with rr%") or val.matches("%current timestamp%") or
    val.matches("%varchar_format%") or val.matches("%substr%") or val.matches("%locate%")
  )
}

// Helper predicate to determine SQL query type
string getSqlQueryType(StringLiteral sql) {
  if sql.getValue().matches("%?%") or sql.getValue().matches("%:%") 
  then result = "parameterized"
  else if sql.getValue().matches("%\"%") or sql.getValue().matches("%'%") 
  then result = "dynamic"
  else result = "static"
}

// Main query to generate JSON output
from StringLiteral sql, Method method, Type declaringType
where 
  containsSql(sql) and
  method = sql.getEnclosingCallable() and
  declaringType = method.getDeclaringType() and
  not sql.getFile().getAbsolutePath().matches("%test%") and
  not sql.getFile().getAbsolutePath().matches("%generated%")
select 
  "{" +
  "\"file\": \"" + sql.getFile().getAbsolutePath() + "\"," +
  "\"class\": \"" + declaringType.getName() + "\"," +
  "\"method\": {" +
    "\"name\": \"" + method.getName() + "\"," +
    "\"start_line\": " + method.getLocation().getStartLine() + "," +
    "\"end_line\": " + method.getLocation().getEndLine() + "," +
    "\"body\": \"" + sql.getValue().replaceAll("\"", "\\\"").replaceAll("\n", "\\n") + "\"," +
    "\"sql_query\": {" +
      "\"type\": \"" + getSqlQueryType(sql) + "\"," +
      "\"query_string\": \"" + sql.getValue().replaceAll("\"", "\\\"").replaceAll("\n", "\\n") + "\"," +
      "\"line_start\": " + sql.getLocation().getStartLine() + "," +
      "\"line_end\": " + sql.getLocation().getEndLine() +
    "}" +
  "}" +
  "}"  
