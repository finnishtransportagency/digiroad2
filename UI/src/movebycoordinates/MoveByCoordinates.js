window.MoveByCoordinates = function() {
    var coordinatesSpan = $('<span class="moveToCoordinates"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" title="lon,lat esim. 6901839,435323"/>');
    var coordinatesMove = $('<input type="button" class="moveToButton" value="Siirry"/>');

    var renderCoordinatesMoveElement = function() {
        $('.mapplugin.coordinates').append(
            coordinatesSpan.append(coordinatesText).append(coordinatesMove)
        );
    };

    var bindEvents = function() {
        var validateCoordinates = function(lonlat) {
            if (lonlat.match("[A-z]")) {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show('Käytä koortinaateissa lon,lat numeroarvoja');
                dialog.fadeout(2000);
                return false;
            }
            return lonlat.match("\\d+,\\d+");
        };

        var transformToPosition = function(lonlat) {
            var position = {
                lat : lonlat.split(',')[0].trim(),
                lon : lonlat.split(',')[1].trim()
            };
            return position;
        };

        coordinatesMove.on('click', function() {
            var lonlat = $('.coordinates .lonlat').val();
            if (validateCoordinates(lonlat)) {
                eventbus.trigger('coordinates:selected',transformToPosition(lonlat));
            }
        });
    };

    var show = function() {
        renderCoordinatesMoveElement();
        bindEvents();
    };
    show();
};