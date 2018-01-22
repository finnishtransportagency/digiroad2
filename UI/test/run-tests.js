require.config({
  paths: {
    'underscore': '../node_modules/underscore/underscore',
    'jquery': '../node_modules/jquery/dist/jquery.min',
    'lodash': '../node_modules/lodash/index',
    'backbone': '../../node_modules/backbone/backbone',
    'chai': '../../node_modules/chai/chai',
    'EventBus': '../src/utils/eventbus',
    "SelectedMassTransitStop": '../src/model/selectedMassTransitStop',
    'Backend': '../src/utils/backend-utils',
    'validitydirections': '../src/utils/validity-directions',
    'GeometryUtils': '../src/utils/geometryUtils',
    'SpeedLimitsCollection': '../src/controller/speedLimitsCollection',
    'RoadCollection': '../src/controller/roadCollection',
    'SelectedSpeedLimit': '../src/model/selectedSpeedLimit',
    'zoomlevels': '../src/utils/zoom-levels',
    'geometrycalculator': '../src/utils/geometry-calculations',
    'LocationInputParser': '../src/utils/locationInputParser',
    'assetGrouping': '../src/assetgrouping/asset-grouping',
    'AssetsTestData': '../test_data/assetsTestData',
    'RoadLinkTestData': '../test_data/roadLinkTestData',
    'UserRolesTestData': '../test_data/userRolesTestData',
    'EnumeratedPropertyValuesTestData': '../test_data/enumeratedPropertyValuesTestData',
    'AssetPropertyNamesTestData': '../test_data/assetPropertyNamesTestData',
    'SpeedLimitsTestData': '../test_data/speedLimitsTestData',
    'SpeedLimitSplitTestData': '../test_data/speedLimitSplitTestData',
    'AssetTypePropertiesTestData': '../test_data/assetTypePropertiesTestData'
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
  'unit-tests/selectedMassTransitStopSpec',
  'unit-tests/geometry-calculations-spec',
  'unit-tests/massTransitStopGroupingSpec',
  'unit-tests/selectedSpeedLimitSpec',
  'unit-tests/locationInputParserSpec'], function (lodash) {
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
