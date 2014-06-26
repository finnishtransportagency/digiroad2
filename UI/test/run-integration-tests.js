require.config({
  paths: {
    'chai': '../../bower_components/chai/chai'
  },
  waitSeconds: 10
});
require(['GroupingByValidityPeriodSpec'], function() {
  if(window.mochaPhantomJS) { mochaPhantomJS.run(); }
  else { mocha.run(); }
});
