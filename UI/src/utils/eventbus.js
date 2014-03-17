(function(BackboneEvents) {
    window.eventbus = BackboneEvents;
    eventbus.on('all', function(eventName, entity) {
        if (window.DR2_LOGGING) {
            console.log(eventName, entity);
        }
    });
})(BackboneEvents);
