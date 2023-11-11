FROM clojure:temurin-17-tools-deps-1.11.1.1413-bullseye-slim

# Install deps
WORKDIR /app
COPY deps.edn deps.edn
RUN clojure -P -X:migrations
