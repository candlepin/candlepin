#! /bin/bash

retry() {
    local -r -i max_attempts="$1"; shift
    local -r name="$1"; shift
    local -r cmd="$@"
    local -i attempt_num=1
    echo -n "Waiting for $name to start..."
    until $cmd
    do
        if (( attempt_num == max_attempts ))
        then
            echo "Attempt $attempt_num failed and there are no more attempts left!"
            return 1
        else
            echo -n '.'
            sleep $(( attempt_num++ ))
        fi
    done
    echo
}

setup_mysql() {
    retry 20 "mysql" mysqladmin --host=db --user=root --password=password status

    mysql --user=root mysql --password=password --host=db --execute="CREATE USER 'candlepin'; GRANT ALL PRIVILEGES on candlepin.* TO 'candlepin' WITH GRANT OPTION"
    mysql --user=root mysql --password=password --host=db --execute="CREATE USER 'gutterball'; GRANT ALL PRIVILEGES on gutterball.* TO 'gutterball' WITH GRANT OPTION"
    mysqladmin --host=db --user="candlepin" create candlepin
    mysqladmin --host=db --user="gutterball" create gutterball

    echo "USE_MYSQL=\"1\"" >> /root/.candlepinrc
}

setup_postgres() {
    # if pg_is ready isn't there source the scl version
    command -v pg_isready 2> /dev/null || source scl_source enable rh-postgresql12 || true
    retry 20 "postgres" pg_isready -h db
}

setup_database() {
  # normalize the flags with true/false
  USING_MYSQL=${USING_MYSQL:-'false'}
  USING_POSTGRES=${USING_POSTGRES:-'false'}

  # set a default
  if [ $USING_MYSQL = false ] && [ $USING_POSTGRES = false ]; then
    # mysql for now
    USING_MYSQL=true
  fi

  if [ $USING_MYSQL = true ]; then
    setup_mysql
  elif [ $USING_POSTGRES = true ]; then
    setup_postgres
  fi
}
