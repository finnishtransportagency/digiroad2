require.config({
    paths: {
        'underscore':               '../bower_components/underscore/underscore',
        'jquery':                   '../bower_components/jquery/dist/jquery.min',
        'lodash':                   '../bower_components/lodash/dist/lodash.min',
        'backbone':                 '../../bower_components/backbone/backbone',
        'chai':                     '../../bower_components/chai/chai',
        'EventBus':                 '../src/utils/eventbus',
        'SelectedAssetModel':       '../src/utils/SelectedAssetModel',
        'LinearAssetLayer':         '../src/bundles/digiroad2/bundle/map/LinearAssetLayer',
        'OpenLayers':               '../bower_components/oskari.org/packages/openlayers/bundle/openlayers-build/OpenLayers',
        'zoomlevels':               '../src/utils/zoomLevels',
        'geometrycalculator':       '../src/utils/geometry-calculations',
        'assetGrouping':            '../src/assetgrouping/asset-grouping'
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
        'SelectedAssetModel': {
            deps: ['EventBus', 'lodash']
        },
        'LinearAssetLayer': {
            exports: 'LinearAssetLayer',
            deps: ['OpenLayers']
        },
        'geometrycalculator': {
            exports: 'geometrycalculator'
        },
        'assetGrouping': {
            exports: 'assetGrouping'
        }
    },
    waitSeconds: 10
});
require(['lodash',
         'SelectedAssetModelSpec',
         'linear-asset-layer-spec',
         'geometry-calculations-spec',
         'asset-grouping-spec'], function(lodash) {
    window._ = lodash;
    mocha.checkLeaks();
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
});
