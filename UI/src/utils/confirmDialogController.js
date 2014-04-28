(function (){
    return {
        initialize: function() {
            eventbus.on('asset:unselected', function() {
                // TODO: Show confirmation dialog if necessary
            });
        }
    };
})();