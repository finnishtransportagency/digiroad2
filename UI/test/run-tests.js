require.config({
    paths: {
        'underscore':               '../bower_components/underscore/underscore',
        'jquery':                   '../bower_components/jquery/dist/jquery.min',
        'lodash':                   '../bower_components/lodash/dist/lodash.min',
        'backbone':                 '../../bower_components/backbone/backbone',
        'chai':                     '../../bower_components/chai/chai',
        'EventBus':                 '../src/utils/eventbus',
        'SelectedAssetController':  '../src/utils/selectedAssetController',
        'LinearAssetLayer':         '../src/bundles/digiroad2/bundle/map/LinearAssetLayer',
        'OpenLayers':               '../bower_components/oskari.org/packages/openlayers/bundle/openlayers-build/OpenLayers',
        'zoomlevels':               '../src/utils/zoomLevels',
        'geometrycalculator':       '../src/utils/geometry-calculations'
    },
    shim: {
        'jquery': { exports: '$' },
        'lodash': { exports: '_' },
        'backbone': {
            deps: ['jquery', 'underscore'],
            exports: 'Backbone'
        },
        'EventBus': {
            deps: ['backbone']
        },
        'SelectedAssetController': {
            deps: ['EventBus', 'lodash']
        },
        'LinearAssetLayer': {
            exports: 'LinearAssetLayer',
            deps: ['OpenLayers']
        },
        'geometrycalculator': {
            exports: 'geometrycalculator'
        }
    },
    waitSeconds: 10
});
require(['lodash',
         'selected-asset-controller-spec',
         'linear-asset-layer-spec',
         'geometry-calculations-spec'], function(lodash) {
    window._ = lodash;
    mocha.checkLeaks();
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
});
