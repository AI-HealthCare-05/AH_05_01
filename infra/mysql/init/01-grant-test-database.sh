#!/bin/bash
set -e

if [ -z "${MYSQL_USER:-}" ]; then
  echo "MYSQL_USER must be set before granting test database privileges." >&2
  exit 1
fi

case "${MYSQL_USER}" in
  *[!A-Za-z0-9_]* )
    echo "MYSQL_USER may contain only letters, numbers, and underscores." >&2
    exit 1
    ;;
esac

# Tortoise ORM creates and drops this isolated database for every test session.
# Restrict the application user to the exact test database instead of granting
# global CREATE/DROP privileges.
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<-EOSQL
GRANT ALL PRIVILEGES ON \`test\`.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL

echo "Granted ${MYSQL_USER} privileges on the isolated test database."
