window.CoordinateSelector = function(parentElement) {
    var tooltip = "Koordinaattien syöttö: pohjoinen (7 merkkiä), itä (6 merkkiä). Esim. 6901839, 435323";
    var crosshairToggle = $('<div class="coordinatesContainer"><input type="checkbox" name="crosshair" value="crosshair" checked="true"/>Näytä kohdistin</div>');
    var coordinatesDiv = $('<div class="coordinatesContainer"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" placeholder="lon, lat" title="' + tooltip +'"/>');
    var submitButton = $('<input type="button" class="moveToButton" value="Siirry"/>');
    
    var render = function() {
        parentElement.append(crosshairToggle).append(coordinatesDiv.append(coordinatesText).append(submitButton));
    };

    var bindEvents = function() {
        var moveToCoordinates = function() {
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
                dialog.show('Käytä koordinaateissa lon, lat numeroarvoja');
                dialog.fadeout(2000);
            }
        };

        coordinatesText.keypress(function(event) {
            if (event.keyCode == 13) {
                moveToCoordinates();
            }
        });
        submitButton.on('click', function() {
            moveToCoordinates();
        });

        $('input', crosshairToggle).change(function() {
            $('.crossHair').toggle(this.checked);
        });
    };

    var show = function() {
        render();
        bindEvents();
    };
    show();
};