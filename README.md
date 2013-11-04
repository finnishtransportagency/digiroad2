digi-road-2
===========


[![Build Status] (https://travis-ci.org/finnishtransportagency/digi-road-2.png)]
(https://travis-ci.org/finnishtransportagency/digi-road-2)


Ympäristön pystytys
===================

Kloonaa digiroad2-repo omalle koneellesi

```
git clone https://github.com/finnishtransportagency/digi-road-2.git
```

[Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))
Asenna [bower](https://github.com/bower/bower)

```
npm install -g bower
```

Hae ja asenna projektin tarvitsemat riippuvuudet

```
npm install && bower install
```

Asenna [grunt](http://gruntjs.com/getting-started)

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
