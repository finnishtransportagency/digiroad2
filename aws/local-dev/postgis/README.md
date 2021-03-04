Digiroad2 PostGIS with Docker Compose
=================================
Docker Compose installs and starts the PostGIS database server for local Digiroad2 development:

- Database: digiroad2
- Username: digiroad2
- Password: digiroad2

**Start the postgis server:**

`docker-compose up`

or

`./start-postgis.sh`

**Start the postgis server on the background:**

`docker-compose up -d`

**Stop the server:**

`docker-compose down`

or

`./stop-postgis.sh`

**Clean the data:**

`docker volume rm postgis_data` 
