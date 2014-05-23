window.CoordinateSelector = function() {
    var coordinatesSpan = $('<span class="moveToCoordinates"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" title="lon,lat esim. 6901839,435323"/>');
    var coordinatesMove = $('<input type="button" class="moveToButton" value="Siirry"/>');
    
    var render = function() {
        $('.mapplugin.coordinates').append(
            coordinatesSpan.append(coordinatesText).append(coordinatesMove)
        );
    };

    var bindEvents = function() {
        coordinatesMove.on('click', function() {
            var lonlat = $('.coordinates .lonlat').val();
            if (lonlat.match("[A-z]")) {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show('Käytää koortinaateissa lon,lat numeroarvoja');
                dialog.fadeout(2000);
            }
            if (lonlat.match("\\d+,\\d+")) {
                var position = {
                    lon: lonlat.split(',')[0].trim(),
                    lat: lonlat.split(',')[1].trim()
                };
                eventbus.trigger('coordinates:selected', position);
            }
        });
    };

    var show = function() {
        render();
        bindEvents();
    };
    show();
};