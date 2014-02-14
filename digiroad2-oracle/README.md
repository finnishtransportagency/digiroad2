digiroad2-oracle
================

Oracle-spesifinen toteutus Digiroad2:n GeometryProvider-rajapinnasta. Tuottaa kirjaston, joka lisätään ajonaikaisesti digiroad2-sovelluksen polkuun.

Build edellyttää, että paikallinen tietokantaymäristö on alustettu ja konfiguroitu:

Luo digiroad2-oracle/conf/dev/bonecp.properties ja lisää sinne tietokantayhteyden tiedot:

```
bonecp.jdbcUrl=jdbc:oracle:thin:@livispr01n1l-vip:1521/drkeh
bonecp.username=<user>
bonecp.password=<password>
```

Skeeman alustus
```
./sbt -Denv=dev
> project digiroad2-oracle
> test:console
scala> fi.liikennevirasto.digiroad2.util.DataFixture.tearDown
scala> fi.liikennevirasto.digiroad2.util.DataFixture.setUp
scala> sys.exit
> exit
```  


