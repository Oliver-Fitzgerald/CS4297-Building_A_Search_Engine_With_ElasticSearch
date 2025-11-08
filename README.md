# CS4297-Building_A_Search_Engine_With_ElasticSearch
A project to be completed by students of CS4297 Applied System Design. It involves  building and deploying a complete, 4 service distributed pipeline using Ethical Web Crawling, MySQL, Kafka, and ElasticSearch orchestrated by Docker Compose

## Build Instructions

## External Documentation

- [Project Tracker](https://docs.google.com/spreadsheets/d/10WftdEzXHP11sBiDwfXjrYq_x7tXw9PFji25JCPlXU8/edit?usp=sharing)
STEPS TO RUN ,


git clone https://github.com/Oliver-Fitzgerald/CS4297-Building_A_Search_Engine_With_ElasticSearch.git
cd CS4297-Building_A_Search_Engine_With_ElasticSearch

# first time setup
cp .env.example .env   # or create your own .env

# launch full stack
docker compose up --build

# in another terminal
./gradlew :crawler:run

# test endpoints
curl "http://localhost:8082/api/search?q=AI"
curl "http://localhost:8082/api/analytics/tags"
curl "http://localhost:8082/api/search/db?q=AI"
