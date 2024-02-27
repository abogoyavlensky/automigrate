# Example minimal setup for Automigrate

This example contains minimal setup for experimenting with Automigrate.
Just add your models to `models.edn` and create migrations!

## Setup

Run the database and a simple admin UI for viewing the db schema:

```shell
$ docker compose build demo
$ docker compose up -d db adminer
$ docker compose run --rm demo /bin/bash
```

Check existing migrations:

```shell
$ clojure -X:migrations list
Migrations not found.
```

## Next steps
To see Automigrate in action you can add a model to `models.edn` as it described 
in [documentation](https://github.com/abogoyavlensky/automigrate#model-definition) and run:

```shell
$ clojure -X:migrations make
```

Then apply migrations:

```shell
$ clojure -X:migrations migrate
```
