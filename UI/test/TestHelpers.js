define(function() {
  var restartApplication = function(callback) {
    eventbus.once('application:initialized', callback);
    Application.restart();
  };

  return {
    restartApplication: restartApplication
  };
});