# Example for automigrated

Run database and simple admin UI for viewing db schema:

```shell
$ docker-compose up -d db adminer
```

Check existing migrations:

```shell
$ docker-compose run demo clojure -X:migrations list
Creating examples_demo_run ... done
[ ] 0001_auto_create_table_book.edn
[ ] 0002_auto_create_table_author.edn
[ ] 0003_auto_add_column_amount.edn
```

Migrate database according to migrations:

```shell
$ docker-compose run demo clojure -X:migrations migrate
Creating examples_demo_run ... done
Migrating: 0001_auto_create_table_book...
Successfully migrated: 0001_auto_create_table_book
Migrating: 0002_auto_create_table_author...
Successfully migrated: 0002_auto_create_table_author
Migrating: 0003_auto_add_column_amount...
Successfully migrated: 0003_auto_add_column_amount
```

Check migrations' status again:

```shell
$ docker-compose run demo clojure -X:migrations list
Creating examples_demo_run ... done
[✓] 0001_auto_create_table_book.edn
[✓] 0002_auto_create_table_author.edn
[✓] 0003_auto_add_column_amount.edn
```

Now you can open adminer UI in browser and check newly created tables by link: [http://localhost:8081/](http://localhost:8081/).

*Fill all credentials as `demo`.*

### Next steps
To see creating in action you can change any model or add new one as it described 
in [documentation](https://github.com/abogoyavlensky/automigrate#model-definition) and run 
`$ docker-compose run demo clojure -X:migrations make`.

Or you could try different commands with different args from documentation section 
[CLI interface](https://github.com/abogoyavlensky/automigrate#cli-interface).
