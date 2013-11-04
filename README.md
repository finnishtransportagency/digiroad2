digi-road-2
===========


[![Build Status] (https://travis-ci.org/finnishtransportagency/digi-road-2.png)]
(https://travis-ci.org/finnishtransportagency/digi-road-2)


Ympäristön pystytys
===================

1. Kloonaa digiroad2-repo omalle koneellesi

```
git clone https://github.com/finnishtransportagency/digi-road-2.git
```

2. [Asenna node.js](http://howtonode.org/how-to-install-nodejs) (samalla asentuu [npm](https://npmjs.org/))
3. Asenna [bower](https://github.com/bower/bower)

```
npm install -g bower
```

4. Hae ja asenna projektin tarvitsemat riippuvuudet

```
npm install && bower install
```

5. Asenna [grunt](http://gruntjs.com/getting-started)

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
