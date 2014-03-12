(function(BackboneEvents) {
    window.eventbus = BackboneEvents;
    eventbus.on('all', function(eventName, entity) {
      console.log(eventName, entity);
    });
})(BackboneEvents);
