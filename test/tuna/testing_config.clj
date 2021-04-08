(ns tuna.testing-config)


(def MIGRATIONS-DIR "test/tuna/migrations/")

(def MODELS-DIR "test/tuna/models/")

(def DATABASE-URL "jdbc:postgresql://localhost:5555/tuna?user=tuna&password=tuna")

(def DATABASE-CONN {:connection-uri DATABASE-URL})
