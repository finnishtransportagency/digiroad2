require.config({
  paths: {
    'chai': '../../bower_components/chai/chai'
  },
  waitSeconds: 10
});
require(['GroupingByValidityPeriodSpec'], function() {
  eventbus.on('application:initialized', function() {
    if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
    else { mocha.run(); }
  });
});
