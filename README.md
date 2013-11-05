digi-road-2
===========


[![Build Status] (https://travis-ci.org/finnishtransportagency/digiroad2.png)]
(https://travis-ci.org/finnishtransportagency/digiroad2)


Ympäristön pystytys
===================

1. Kloonaa digiroad2-repo omalle koneellesi

  ```
  git clone https://github.com/finnishtransportagency/digi-road-2.git
  ```

1. [Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))
1. Asenna [bower](https://github.com/bower/bower)

  ```
  npm install -g bower
  ```

1. Hae ja asenna projektin tarvitsemat riippuvuudet

  ```
  npm install && bower install
  ```

1. Asenna [grunt](http://gruntjs.com/getting-started)

  ```
  npm install -g grunt-cli
  ```

Ajaminen
========

Buildin rakentaminen: 
```
grunt
```

Testien ajaminen:
```
grunt test
```

Kehitysserverin pystytys:
```
grunt connect watch
```
Kehitysserveri ajaa automaattisesti testit, kääntää lessit ja toimii watch -tilassa.
