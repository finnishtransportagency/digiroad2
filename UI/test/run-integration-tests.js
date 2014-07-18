require.config({
  paths: {
    'jquery': '../../bower_components/jquery/dist/jquery.min',
    'chai': '../../bower_components/chai/chai',
    'chai-jquery': '../../bower_components/chai-jquery/chai-jquery',
    'eventbus': '../src/utils/eventbus',
    'AssetsTestData': '../test_data/AssetsTestData'
  },
  shim: {
    'chai-jquery': ['jquery', 'chai'],
    'eventbus': { exports: 'eventbus' },
    'AssetsTestData': { exports: 'AssetsTestData' }
  },
  waitSeconds: 10
});
require(['chai', 'chai-jquery', 'GroupingByValidityPeriodSpec', 'AssetCreationSpec', 'AssetMoveSpec'], function(chai, chaiJquery) {
  chai.use(chaiJquery);

  eventbus.once('application:initialized', function() {
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });
});
