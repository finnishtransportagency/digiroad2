require.config({
  paths: {
    'jquery': '../../bower_components/jquery/dist/jquery.min',
    'chai': '../../bower_components/chai/chai',
    'chai-jquery': '../../bower_components/chai-jquery/chai-jquery'
  },
  shim: {
    'chai-jquery': ['jquery', 'chai']
  },
  waitSeconds: 10
});
require(['chai', 'chai-jquery', 'GroupingByValidityPeriodSpec', 'AssetCreationSpec'], function(chai, chaiJquery) {
  chai.use(chaiJquery);

  eventbus.on('application:initialized', function() {
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });
});
