window.CoordinateSelector = function(parentElement) {
    var coordinatesSpan = $('<span class="moveToCoordinates"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" title="lon,lat esim. 6901839,435323"/>');
    var submitButton = $('<input type="button" class="moveToButton" value="Siirry"/>');
    
    var render = function() {
        parentElement.append(coordinatesSpan.append(coordinatesText).append(submitButton));
    };

    var bindEvents = function() {
        submitButton.on('click', function() {
            var lonlat = $('.coordinates .lonlat').val();
            var regex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
            var result = lonlat.match(regex);
            if (result) {
                var position = {
                    lon: result[1],
                    lat: result[2]
                };
                eventbus.trigger('coordinates:selected', position);
            } else {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show('Käytää koortinaateissa lon,lat numeroarvoja');
                dialog.fadeout(2000);
            }
        });
    };

    var show = function() {
        render();
        bindEvents();
    };
    show();
};