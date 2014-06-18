(function(root) {
    root.MassTransitStop = function(data) {
        var cachedMarker = null;
        var cachedDirectionArrow = null;

        var createDirectionArrow = function() {
            var getAngleFromBearing = function(bearing, validityDirection) {
                if (bearing === null || bearing === undefined) {
                    console.log('Bearing was null, find out why');
                    return 90;
                }
                return bearing + (90 * validityDirection);
            };
            var validityDirection = (data.validityDirection === 3) ? 1 : -1;
            var angle = getAngleFromBearing(data.bearing, validityDirection);
            return new OpenLayers.Feature.Vector(
                new OpenLayers.Geometry.Point(data.group ? data.group.lon : data.lon, data.group ? data.group.lat : data.lat),
                null,
                {
                    externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg',
                    graphicHeight: 16, graphicWidth: 30, graphicXOffset:-15, graphicYOffset:-8, rotation: angle
                }
            );
        };

        var getMarker = function(shouldCreate) {
            if (shouldCreate || !cachedMarker) {
                cachedMarker = new MassTransitMarker(data).createMarker();
            }
            return cachedMarker;
        };

        var createNewMarker = function() {
            cachedMarker = new MassTransitMarker(data).createNewMarker();
            return cachedMarker;
        };

        var getDirectionArrow = function(shouldCreate) {
            if (shouldCreate || !cachedDirectionArrow) {
                cachedDirectionArrow = createDirectionArrow();
            }
            return cachedDirectionArrow;
        };

        return {
            getMarker: getMarker,
            createNewMarker : createNewMarker,
            getDirectionArrow: getDirectionArrow
        };
  };
}(this));
