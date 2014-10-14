require.config({
    paths: {
        'underscore':               '../bower_components/underscore/underscore',
        'jquery':                   '../bower_components/jquery/dist/jquery.min',
        'lodash':                   '../bower_components/lodash/dist/lodash.min',
        'backbone':                 '../../bower_components/backbone/backbone',
        'chai':                     '../../bower_components/chai/chai',
        'EventBus':                 '../src/utils/eventbus',
        'SelectedAssetModel':       '../src/model/SelectedAssetModel',
        'SpeedLimitLayer':          '../src/view/SpeedLimitLayer',
        'GeometryUtils':            '../src/utils/GeometryUtils',
        'SpeedLimitsCollection':    '../src/model/SpeedLimitsCollection',
        'SelectedSpeedLimit':       '../src/model/SelectedSpeedLimit',
        'zoomlevels':               '../src/utils/zoom-levels',
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
        'SpeedLimitLayer': {
            exports: 'SpeedLimitLayer'
        },
        'SpeedLimitsCollection': {
            exports: 'SpeedLimitsCollection'
        },
        'SelectedSpeedLimit': {
            exports: 'SelectedSpeedLimit'
        },
        'geometrycalculator': {
            exports: 'geometrycalculator'
        },
        'GeometryUtils': {
          exports: 'GeometryUtils'
        },
        'assetGrouping': {
            exports: 'AssetGrouping'
        }
    },
    waitSeconds: 10
});
require(['lodash',
         'SelectedAssetModelSpec',
         'speed-limit-layer-spec',
         'geometry-calculations-spec',
         'asset-grouping-spec'], function(lodash) {
    window._ = lodash;
    mocha.checkLeaks();
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
});
