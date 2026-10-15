"""Export local Connectify MariaDB data as PostgreSQL INSERT statements.

The generated file must stay outside Git because it contains user data.
"""

from __future__ import annotations

import json
import subprocess
import sys
from decimal import Decimal
from pathlib import Path

MYSQL = Path(r"C:\xampp\mysql\bin\mysql.exe")
DATABASE = "connectify_db"
TABLES = [
    "categories", "users", "user_accounts", "events",
    "events_design_template_backup_20260709", "client_favorites",
    "event_admin_records", "event_presentation_settings", "internal_messages",
    "ticket_types", "purchases", "tickets", "notifications", "shopping_carts",
    "shopping_cart_items", "shopping_cart_consumptions", "izipay_web_attempts",
]
IDENTITY_TABLES = [
    "categories", "users", "user_accounts", "events", "client_favorites",
    "event_admin_records", "event_presentation_settings", "internal_messages",
    "ticket_types", "purchases", "tickets", "notifications", "shopping_carts",
    "shopping_cart_items",
]
NUMERIC_TYPES = {
    "bigint", "decimal", "double", "float", "int", "integer", "mediumint",
    "numeric", "real", "smallint", "tinyint",
}


def mysql(query: str) -> str:
    result = subprocess.run(
        [str(MYSQL), "--protocol=tcp", "--host=127.0.0.1", "--port=3308",
         "--user=root", "--default-character-set=utf8mb4", "--batch", "--raw",
         "--skip-column-names", f"--database={DATABASE}", f"--execute={query}"],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return result.stdout.strip()


def quote_identifier(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'


def sql_literal(value: object, data_type: str) -> str:
    if value is None:
        return "NULL"
    if data_type == "bit":
        return "TRUE" if int(value) else "FALSE"
    if data_type in NUMERIC_TYPES:
        return format(value, "f") if isinstance(value, Decimal) else str(value)
    text = str(value).replace("\x00", "").replace("'", "''")
    return "'" + text + "'"


def columns_for(table: str) -> list[tuple[str, str]]:
    rows = mysql(
        "select column_name, data_type from information_schema.columns "
        f"where table_schema='{DATABASE}' and table_name='{table}' order by ordinal_position"
    )
    return [tuple(line.split("\t", 1)) for line in rows.splitlines() if line]


def rows_for(table: str, columns: list[tuple[str, str]]) -> list[dict[str, object]]:
    pairs: list[str] = []
    for column, data_type in columns:
        identifier = "`" + column.replace("`", "``") + "`"
        expression = f"cast({identifier} as unsigned)" if data_type == "bit" else identifier
        pairs.extend(["'" + column.replace("'", "''") + "'", expression])
    query = (
        "set session group_concat_max_len=100000000; "
        "select concat('[', coalesce(group_concat(json_object("
        + ",".join(pairs) + ") separator ','), ''), ']') from `"
        + table.replace("`", "``") + "`;"
    )
    payload = mysql(query) or "[]"
    return json.loads(payload, parse_float=Decimal, parse_int=int)


def export(destination: Path) -> dict[str, int]:
    statements = ["begin;", "set search_path to connectify;"]
    counts: dict[str, int] = {}
    for table in TABLES:
        columns = columns_for(table)
        rows = rows_for(table, columns)
        counts[table] = len(rows)
        names = ", ".join(quote_identifier(column) for column, _ in columns)
        for row in rows:
            values = ", ".join(sql_literal(row.get(column), data_type) for column, data_type in columns)
            statements.append(f"insert into {quote_identifier(table)} ({names}) values ({values});")

    for table in IDENTITY_TABLES:
        statements.append(
            "select setval(pg_get_serial_sequence('connectify." + table
            + "', 'id'), coalesce(max(id), 1), max(id) is not null) from "
            + quote_identifier(table) + ";"
        )
    statements.append("commit;")
    destination.write_text("\n".join(statements) + "\n", encoding="utf-8")
    return counts


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: export_mariadb_to_postgres.py DESTINATION.sql")
    target = Path(sys.argv[1]).resolve()
    counts = export(target)
    print(json.dumps({"destination": str(target), "rows": counts}, ensure_ascii=False))
