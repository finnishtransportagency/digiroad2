window.MoveByCoordinates = function() {
    var coordinatesSpan = $('<span class="moveToCoordinates"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" title="lon,lat esim. 6901839,435323"/>');
    var coordinatesMove = $('<input type="button" class="moveToButton" value="Siirry"/>');
    var coordinatesAdd = $('<input type="button" class="addToButton" value="Lisää"/>');

    var renderCoordinatesMoveElement = function() {
        $('.mapplugin.coordinates').append(
            coordinatesSpan.append(coordinatesText).append(coordinatesMove).append(coordinatesAdd)
        );
    };

    var bindEvents = function() {
        var sanityCheck = function(lonlat) {
            if (lonlat.match("[A-z]")) {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show('Käytää koortinaateissa lon,lat numeroarvoja');
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
            if(sanityCheck(lonlat)) {
                eventbus.trigger('coordinates:selected',transformToPosition(lonlat));
            }
        });

        coordinatesAdd.on('click' , function() {
            var lonlat = $('.coordinates .lonlat').val();
            if(sanityCheck(lonlat)) {
                eventbus.trigger('add:asset', transformToPosition(lonlat));
            }
        });
    };

    var show = function() {
        coordinatesAdd.hide();
        renderCoordinatesMoveElement();
        bindEvents();
    };

    eventbus.on('application:readOnly', function (readOnly) {
        if (readOnly) {
            coordinatesAdd.hide();
        } else {
            coordinatesAdd.show();
        }
    });
    show();
};