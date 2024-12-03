(function(root) {
    root.PopUpMapView = function(map, backend, layer) {
        eventbus.on('manoeuvresOnExpiredLinks:fetched', function (pos) {
            var posX = parseInt(pos.x);
            var posY = parseInt(pos.y);
            map.getView().setCenter([posX, posY]);
            map.getView().setZoom(12);
            backend.getMunicipalityFromCoordinates(posX, posY, function(vkmResult) {
               var municipalityName = !_.isEmpty(vkmResult) && vkmResult.properties.kuntanimi ? vkmResult.properties.kuntanimi : "Tuntematon";
               $('#pop-up-municipality-name-container')
                   .append('<div class="pop-up-municipality-name-container-entry">ETRS89-TM35FIN P: ' + posY + '  I: ' + posX, +'</div>')
                   .append('<div class="pop-up-municipality-name-container-entry">' + municipalityName + ':' + '</div>')
                   .append('<button id="copy-coordinate-btn" class="btn btn-sm copy-button" ' +
                       'data-pos-x="' + posX + '" data-pos-y="' + posY + '">' +
                       'Kopioi kohteen koordinaatit' +
                       '</button>');
            });
        }, this);

        $(document).on('click', '#closeMapButton', function() {
            map.getInteractions().clear();
            map.getOverlays().clear();
            map.getLayers().clear();
            map.setTarget(null);
            eventbus.off('manoeuvresOnExpiredLinks:fetched');
            layer.hide();
            $("#mapModal").remove();
        });

        $(document).on('click', '#copy-coordinate-btn', function() {
            var button = $(this);
            setTimeout(function () {
                button.blur();
            }, 100);

            var posX = parseInt(button.data('pos-x'));
            var posY = parseInt(button.data('pos-y'));
            var posString = posY + ', ' + posX;

            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(posString)
                    .then(function() {
                        showNotification('success', 'Kohteen koordinaatit (' + posString + ') kopioitu leikepöydälle!', button);
                    })
                    .catch(function(err) {
                        showNotification('failure', 'Kopiointi epäonnistui!', button);
                    });
            } else {
                alert('Kopiointia leikepöydälle ei tueta tässä selaimessa.');
            }
        });

        function showNotification(type, message, button) {
            var notification = $('<div class="pop-up-copy-notification ' + type + '">')
                .text(message)
                .css({
                    top: button.offset().top - 40 + 'px',
                    left: button.offset().left + 'px'
                });

            $('body').append(notification);
            setTimeout(function() {
                notification.css('opacity', '1');
            }, 10);
            setTimeout(function() {
                notification.fadeOut(300, function() {
                    notification.remove();
                });
            }, 2000);
        }
    };
})(this);