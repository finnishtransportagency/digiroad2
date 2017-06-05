require.config({
  paths: {
    'underscore': '../bower_components/underscore/underscore',
    'jquery': '../bower_components/jquery/dist/jquery.min',
    'lodash': '../bower_components/lodash/lodash.min',
    'backbone': '../../bower_components/backbone/backbone',
    'chai': '../../bower_components/chai/chai',
    'EventBus': '../src/utils/eventbus',
    'Backend': '../src/utils/backend-utils',
    'GeometryUtils': '../src/utils/GeometryUtils',
    'RoadCollection': '../src/model/RoadCollection',
    'zoomlevels': '../src/utils/zoom-levels',
    'geometrycalculator': '../src/utils/geometry-calculations',
    'LocationInputParser': '../src/utils/LocationInputParser',
    'RoadAddressTestData': '../test_data/RoadAddressTestData',
    'RoadLinkTestData': '../test_data/RoadLinkTestData',
    'UserRolesTestData': '../test_data/UserRolesTestData'
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
    'Layer': {exports: 'Layer'},
    'RoadCollection': {
      exports: 'RoadCollection'
    },
    'geometrycalculator': {
      exports: 'geometrycalculator'
    },
    'LocationInputParser': { exports: 'LocationInputParser' },
    'GeometryUtils': {
      exports: 'GeometryUtils'
    },
    'RoadAddressTestData': {exports: 'RoadAddressTestData'},
    'RoadLinkTestData': {exports: 'RoadLinkTestData'},
    'UserRolesTestData': {exports: 'UserRolesTestData'},
    'validitydirections': {exports: 'validitydirections'}
  },
  waitSeconds: 10
});
require(['lodash',
  'geometry-calculations-spec',
  'LocationInputParserSpec'], function (lodash) {
  window._ = lodash;
  mocha.checkLeaks();
  if (window.mochaPhantomJS) {
    mochaPhantomJS.run();
  }
  else {
    mocha.run();
  }
});
