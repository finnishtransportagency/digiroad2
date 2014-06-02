require.config({
    paths: {
        'mocha': '../../bower_components/mocha/mocha'
    },
    shim: {
        'mocha': { exports: 'mocha' }
    },
    waitSeconds: 10
});
require(['mocha'], function(mocha) {
    mocha.checkLeaks();
    if (navigator.userAgent.indexOf('PhantomJS') < 0) { mocha.run(); }
});
