# Example for automigrate

This example shows a couple of models and several migrations for it. 

Run the database and a simple admin UI for viewing the db schema:

```shell
$ docker-compose up -d db adminer
```

Check existing migrations:

```shell
$ docker-compose run demo clojure -X:migrations list
Building demo
...
Creating examples_demo_run ... done
Existing migrations:

[ ] 0001_auto_create_table_book.edn
[ ] 0002_auto_create_table_author_etc.edn
[ ] 0003_auto_add_column_amount_to_book_etc.edn
```

Migrate database according to migrations:

```shell
$ docker-compose run demo clojure -X:migrations migrate
Creating examples_demo_run ... done
Applying 0001_auto_create_table_book...
0001_auto_create_table_book successfully applied.
Applying 0002_auto_create_table_author_etc...
0002_auto_create_table_author_etc successfully applied.
Applying 0003_auto_add_column_amount_to_book_etc...
0003_auto_add_column_amount_to_book_etc successfully applied.
```

Check migration status again:

```shell
$ docker-compose run demo clojure -X:migrations list
Creating examples_demo_run ... done
Existing migrations:

[✓] 0001_auto_create_table_book.edn
[✓] 0002_auto_create_table_author_etc.edn
[✓] 0003_auto_add_column_amount_to_book_etc.edn
```

Now you can open the adminer UI in browser and check newly created tables by link: [http://localhost:8081/](http://localhost:8081/).

:information_source: *Choose `System` as `PostgreSQL` and fill all credentials as `demo`.*

### Next steps
To see auto-migration in action you can change any model or add new one as it described 
in [documentation](https://github.com/abogoyavlensky/automigrate#model-definition) and run 
`$ docker-compose run demo clojure -X:migrations make`.

Or you could try different commands with different args from documentation section 
[CLI interface](https://github.com/abogoyavlensky/automigrate#cli-interface).
