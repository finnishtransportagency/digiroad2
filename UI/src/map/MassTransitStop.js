(function(root) {
    root.MassTransitStop = function(data) {
        var unknownAssetType = '99';
        var cachedMarker = null;
        var cachedDirectionArrow = null;

        var createIcon = function() {
            var createIconImages = function(imageIds) {
                var callout = document.createElement("div");
                callout.className = "callout";
                var arrowContainer = document.createElement("div");
                arrowContainer.className = "arrow-container";
                var arrow = document.createElement("div");
                arrow.className = "arrow";
                _.each(imageIds, function (imageId) {
                    var img = document.createElement("img");
                    img.setAttribute("src", "api/images/" + imageId + ".png");
                    callout.appendChild(img);
                });
                arrowContainer.appendChild(arrow);
                callout.appendChild(arrowContainer);
                var dropHandle = document.createElement("div");
                dropHandle.className="dropHandle";
                callout.appendChild(dropHandle);
                return callout;
            };

            var size;
            var imageIds = data.imageIds.length > 0 ? data.imageIds : [unknownAssetType + '_'];
            if (imageIds.length > 1) {
                size = new OpenLayers.Size(28, ((15 * imageIds.length) + (imageIds.length - 1)));
            } else {
                size = new OpenLayers.Size(28, 16);
            }
            var offset = new OpenLayers.Pixel(0, -size.h-9);
            var icon = new OpenLayers.Icon("", size, offset);
            icon.imageDiv.className = "callout-wrapper";
            icon.imageDiv.removeChild(icon.imageDiv.getElementsByTagName("img")[0]);
            icon.imageDiv.setAttribute("style", "");
            icon.imageDiv.appendChild(createIconImages(imageIds));
            return icon;
        };

        var createMarker = function() {
            return new OpenLayers.Marker(new OpenLayers.LonLat(data.lon, data.lat), createIcon());
        };

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
                new OpenLayers.Geometry.Point(data.lon, data.lat),
                null,
                {
                    externalGraphic: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg',
                    graphicHeight: 16, graphicWidth: 30, graphicXOffset:-15, graphicYOffset:-8, rotation: angle
                }
            );
        };

        var getMarker = function(shouldCreate) {
            if (shouldCreate || !cachedMarker) {
                cachedMarker = createMarker();
            }
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
            getDirectionArrow: getDirectionArrow
        };
    };
})(this);
