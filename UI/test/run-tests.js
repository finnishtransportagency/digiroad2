require.config({
  paths: {
    'underscore': '../bower_components/underscore/underscore',
    'jquery': '../bower_components/jquery/dist/jquery.min',
    'lodash': '../bower_components/lodash/lodash.min',
    'backbone': '../../bower_components/backbone/backbone',
    'chai': '../../bower_components/chai/chai',
    'EventBus': '../src/utils/eventbus',
    "SelectedMassTransitStop": '../src/model/SelectedMassTransitStop',
    'Backend': '../src/utils/backend-utils',
    'validitydirections': '../src/utils/validity-directions',
    'GeometryUtils': '../src/utils/GeometryUtils',
    'SpeedLimitsCollection': '../src/model/SpeedLimitsCollection',
    'RoadCollection': '../src/model/RoadCollection',
    'SelectedSpeedLimit': '../src/model/SelectedSpeedLimit',
    'zoomlevels': '../src/utils/zoom-levels',
    'geometrycalculator': '../src/utils/geometry-calculations',
    'LocationInputParser': '../src/utils/LocationInputParser',
    'assetGrouping': '../src/assetgrouping/asset-grouping',
    'AssetsTestData': '../test_data/AssetsTestData',
    'RoadLinkTestData': '../test_data/RoadLinkTestData',
    'UserRolesTestData': '../test_data/UserRolesTestData',
    'EnumeratedPropertyValuesTestData': '../test_data/EnumeratedPropertyValuesTestData',
    'AssetPropertyNamesTestData': '../test_data/AssetPropertyNamesTestData',
    'SpeedLimitsTestData': '../test_data/SpeedLimitsTestData',
    'SpeedLimitSplitTestData': '../test_data/SpeedLimitSplitTestData',
    'AssetTypePropertiesTestData': '../test_data/AssetTypePropertiesTestData'
  },
  shim: {
    'jquery': {exports: '$'},
    'lodash': {exports: '_'},
    'backbone': {
      deps: ['jquery', 'underscore'],
      exports: 'Backbone'
    },
    'EventBus': {
      deps: ['backbone']
    },
    "SelectedMassTransitStop": {
      deps: ['EventBus', 'lodash']
    },
    'Layer': {exports: 'Layer'},
    'SpeedLimitLayer': {
      exports: 'SpeedLimitLayer',
      deps: ['EventBus']
    },
    'SpeedLimitsCollection': {
      exports: 'SpeedLimitsCollection'
    },
    'RoadCollection': {
      exports: 'RoadCollection'
    },
    'SelectedSpeedLimit': {
      exports: 'SelectedSpeedLimit',
      deps: ['validitydirections']
    },
    'geometrycalculator': {
      exports: 'geometrycalculator'
    },
    'LocationInputParser': { exports: 'LocationInputParser' },
    'GeometryUtils': {
      exports: 'GeometryUtils'
    },
    'assetGrouping': {
      exports: 'AssetGrouping'
    },
    'AssetsTestData': {exports: 'AssetsTestData'},
    'RoadLinkTestData': {exports: 'RoadLinkTestData'},
    'UserRolesTestData': {exports: 'UserRolesTestData'},
    'EnumeratedPropertyValuesTestData': {exports: 'EnumeratedPropertyValuesTestData'},
    'AssetPropertyNamesTestData': {exports: 'AssetPropertyNamesTestData'},
    'SpeedLimitsTestData': {exports: 'SpeedLimitsTestData'},
    'SpeedLimitSplitTestData': {exports: 'SpeedLimitSplitTestData'},
    'AssetTypePropertiesTestData': {exports: 'AssetTypePropertiesTestData'},
    'validitydirections': {exports: 'validitydirections'}
  },
  waitSeconds: 10
});
require(['lodash',
  'SelectedMassTransitStopSpec',
  'geometry-calculations-spec',
  'MassTransitStopGroupingSpec',
  'SelectedSpeedLimitSpec',
  'LocationInputParserSpec'], function (lodash) {
  window._ = lodash;
  window.applicationModel = {
    getWithRoadAddress : function(){
      return 'false';
    }
  };
  mocha.checkLeaks();
  if (window.mochaPhantomJS) {
    mochaPhantomJS.run();
  }
  else {
    mocha.run();
  }
});
