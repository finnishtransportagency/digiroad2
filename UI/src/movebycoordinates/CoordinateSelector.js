window.CoordinateSelector = function(parentElement, extent) {
    var tooltip = "Koordinaattien sy&ouml;tt&ouml;: pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;). Esim. 6901839, 435323";
    var crosshairToggle = $('<div class="coordinatesContainer"><input type="checkbox" name="crosshair" value="crosshair" checked="true"/>N&auml;yt&auml; kohdistin</div>');
    var coordinatesDiv = $('<div class="coordinatesContainer"/>');
    var coordinatesText = $('<input type="text" class="lonlat" name="lonlat" placeholder="lon, lat" title="' + tooltip +'"/>');
    var moveButton = $('<input type="button" class="moveToButton" value="Siirry"/>');
    var markButton = $('<input type="button" class="markToButton" value="Merkitse"/>');

    var render = function() {
        parentElement.append(crosshairToggle).append(coordinatesDiv.append(coordinatesText).append(moveButton).append(markButton));
    };

    var bindEvents = function() {
        var moveToCoordinates = function(eventName) {
            var lonlat = $('.coordinates .lonlat').val();
            var regex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
            var result = lonlat.match(regex);

            var showDialog = function(message) {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show(message);
                dialog.fadeout(2000);
            };

            if (!result) {
                showDialog('K&auml;yt&auml; koordinaateissa P ja I numeroarvoja.');
            } else if (!geometrycalculator.isInBounds(extent, result[2], result[1])) {
                showDialog('Koordinaatit eiv&auml;t osu kartalle.');
            } else {
                var position = {
                    lon: result[1],
                    lat: result[2]
                };
                eventbus.trigger(eventName, position);
            }
        };

        coordinatesText.keypress(function(event) {
            if (event.keyCode == 13) {
                moveToCoordinates();
            }
        });
        moveButton.on('click', function() {
            moveToCoordinates('coordinates:selected');
        });
        markButton.on('click', function() {
            moveToCoordinates('coordinates:marked');
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