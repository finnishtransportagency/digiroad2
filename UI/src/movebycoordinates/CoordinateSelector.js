window.CoordinateSelector = function(parentElement) {
    var tooltip = "Koordinaattien syöttö: pohjoinen (7 merkkiä), itä (6 merkkiä). Esim. 6901839, 435323";
    var coordinatesSpan = $('<span class="moveToCoordinates"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" title="' + tooltip +'"/>');
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
                dialog.show('Käytä koortinaateissa lon,lat numeroarvoja');
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