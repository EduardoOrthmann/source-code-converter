import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

predicate containsSql(StringLiteral lit) {
  exists(string val | val = lit.getValue().toLowerCase() |
    // Standard SQL keywords
    val.matches("%select%") or val.matches("%insert%") or val.matches("%update%") or
    val.matches("%delete%") or val.matches("%create%") or val.matches("%alter%") or
    val.matches("%drop%") or val.matches("%from%") or val.matches("%where%") or
    val.matches("%join%") or val.matches("%group by%") or val.matches("%order by%") or
    val.matches("%sysibm%") or val.matches("%syscat%") or val.matches("%sysstat%") or
    val.matches("%fetch first%") or val.matches("%rows only%") or
    val.matches("%with ur%") or val.matches("%with cs%") or val.matches("%with rs%") or
    val.matches("%with rr%") or val.matches("%current timestamp%") or
    val.matches("%varchar_format%") or val.matches("%substr%") or val.matches("%locate%")
  )
}

string getSqlQueryType(StringLiteral sql) {
  exists(string val | val = sql.getValue() |
    if (val.matches("%?%") or val.matches("%:%") or val.matches("%#{%") or val.matches("%${%"))
    then result = "PARAMETERIZED"

    else if
      exists(BinaryExpr be |
        be instanceof AddExpr and
        be.getAnOperand() = sql
      ) or
      exists(MethodAccess ma |
        ma.getMethod().getName() = "append" and
        DataFlow::localExprFlow(sql, ma.getAnArgument())
      )
    then result = "DYNAMIC"
    
    else result = "STATIC"
  )
}

// Main query to generate JSON output
from StringLiteral sql, Method method, Type declaringType, File file
where 
  containsSql(sql) and
  method = sql.getEnclosingCallable() and
  declaringType = method.getDeclaringType() and
  file = sql.getFile() and
  not sql.getFile().getAbsolutePath().matches("%test%") and
  not sql.getFile().getAbsolutePath().matches("%generated%") and
  not sql.getFile().getAbsolutePath().matches("%/target/classes%")
select 
  "{" +
    "\"id\": \"" + sql.getFile().getAbsolutePath() + ":" + sql.getLocation().getStartLine() + "\"," +
    "\"type\": \"" + getSqlQueryType(sql) + "\"," +
    "\"file\": {" +
      "\"path\": \"" + file.getAbsolutePath() + "\"," +
      "\"language\": \"" + file.getExtension().toUpperCase() + "\"" +
    "}," +
    "\"location\": {" +
      "\"startLine\": " + sql.getLocation().getStartLine() + "," +
      "\"endLine\": " + sql.getLocation().getEndLine() + "," +
      "\"startColumn\": " + sql.getLocation().getStartColumn() + "," +
      "\"endColumn\": " + sql.getLocation().getEndColumn() +
    "}," +
    "\"extractedSql\": \"" + sql.getValue().replaceAll("\"", "\"\"").replaceAll("\n", "\\n").replaceAll("\r", "") + "\"," +
    "\"codeContext\": {" +
      "\"containingClass\": \"" + declaringType.getName() + "\"," +
      "\"containingMethod\": \"" + method.getName() + "\"," +
      "\"variableBindings\": [" +
        "{" +
          "\"name\": \"" + "PLACEHOLDER" + "\"," +
          "\"javaType\": \"" + "PLACEHOLDER" + "\"," +
          "\"context\": \"" + "PLACEHOLDER" + "\"" +
        "}" +
      "]" +
    "}," +
    "\"methodParameters\": \"" + concat(Parameter p | p = method.getAParameter() | p.getType().getName() + " " + p.getName(), ", ") + "\"" +
  "}"  
