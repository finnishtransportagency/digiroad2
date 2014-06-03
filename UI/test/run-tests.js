require.config({
    paths: {
        'underscore':               '../bower_components/underscore/underscore',
        'jquery':                   '../bower_components/jquery/dist/jquery.min',
        'lodash':                   '../bower_components/lodash/dist/lodash.min',
        'backbone':                 '../../bower_components/backbone/backbone',
        'chai':                     '../../bower_components/chai/chai',
        'mocha':                    '../../bower_components/mocha/mocha',
        'EventBus':                 '../src/utils/eventbus',
        'SelectedAssetController':  '../src/utils/selectedAssetController'
    },
    shim: {
        'jquery': { exports: '$' },
        'lodash': { exports: '_' },
        'backbone': {
            deps: ['jquery', 'underscore'],
            exports: 'Backbone'
        },
        'mocha': { exports: 'mocha' },
        'EventBus': {
            deps: ['backbone']
        },
        'SelectedAssetController': {
            deps: ['EventBus', 'lodash']
        }
    },
    waitSeconds: 10
});
require(['mocha'], function(mocha) {
    mocha.setup({ui: 'bdd', reporter: 'html'});
    require(['lodash', 'selected-asset-controller-spec'], function(lodash) {
        window._ = lodash;
        mocha.checkLeaks();
        if (navigator.userAgent.indexOf('PhantomJS') < 0) {
            mocha.run();
        }
    });
});
