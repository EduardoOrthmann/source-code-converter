import java

predicate containsSql(StringLiteral lit) {
  exists(string val | val = lit.getValue().toLowerCase() |
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

from StringLiteral sql
where containsSql(sql)
select sql, sql.getFile().getAbsolutePath(), sql.getLocation().getStartLine(), sql.getValue()