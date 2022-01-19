FROM clojure:openjdk-11-tools-deps-1.10.3.1040-slim-buster

# Install deps
WORKDIR /app
COPY deps.edn deps.edn
RUN clojure -P -X:migrations
