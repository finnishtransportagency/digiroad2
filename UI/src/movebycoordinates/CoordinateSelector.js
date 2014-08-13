window.CoordinateSelector = function(parentElement, extent) {
    var tooltip = "Koordinaattien sy&ouml;tt&ouml;: pohjoinen (7 merkki&auml;), it&auml; (6 merkki&auml;). Esim. 6901839, 435323";
    var crosshairToggle = $('<div class="crosshair-wrapper"><div class="checkbox"><label><input type="checkbox" name="crosshair" value="crosshair" checked="true"/> Kohdistin</label></div></div>');
    var coordinatesDiv = $('<div class="coordinates-wrapper"/>');
    var coordinatesText = $('<input type="text" class="lonlat form-control input-sm" name="lonlat" placeholder="P, I" title="' + tooltip +'"/>');
    var moveButton = $('<button class="btn btn-sm btn-tertiary">Siirry</button>');
    var markButton = $('<button class="btn btn-sm btn-tertiary">Merkitse</button>');

    var render = function() {
        parentElement.append(coordinatesDiv.append(coordinatesText).append(moveButton).append(markButton)).append(crosshairToggle);
    };

    var bindEvents = function() {
        var moveToCoordinates = function(eventName) {
            var lonlat = $('.coordinates .lonlat').val();
            var regex = /^\s*(\d+)\s*,\s*(\d+)\s*$/;
            var result = lonlat.match(regex);

            var showDialog = function(message) {
                var dialog = Oskari.clazz.create('Oskari.userinterface.component.Popup');
                dialog.show(message);
                dialog.fadeout(3000);
            };

            if (!result) {
                showDialog('K&auml;yt&auml; koordinaateissa P ja I numeroarvoja.');
            } else if (!geometrycalculator.isInBounds(extent, result[2], result[1])) {
                showDialog('Koordinaatit eiv&auml;t osu kartalle.');
            } else {
                var position = {
                    lat: result[1],
                    lon: result[2]
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
            $('.crosshair').toggle(this.checked);
        });
    };

    var show = function() {
        render();
        bindEvents();
    };
    show();
};