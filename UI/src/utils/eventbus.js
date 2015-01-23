(function(BackboneEvents) {
  window.eventbus = BackboneEvents;
  eventbus.on('all', function(eventName, entity) {
    if (window.DR2_LOGGING && eventName !== 'map:mouseMoved') {
      console.log(eventName, entity);
    }
  });
})(Backbone.Events);
