(function(BackboneEvents) {
  window.eventbus = BackboneEvents;
  eventbus.on('all', function(eventName, entity) {
    if (eventName !== 'map:mouseMoved') {
      console.log(eventName, entity);
    }
  });
  eventbus.oncePromise = function(eventName) {
    var eventReceived = $.Deferred();
    eventbus.once(eventName, function() {
      eventReceived.resolve();
    });
    return eventReceived;
  };
})(Backbone.Events);
