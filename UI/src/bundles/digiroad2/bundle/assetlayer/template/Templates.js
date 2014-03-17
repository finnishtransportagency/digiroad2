Oskari.clazz.define('Oskari.digiroad2.bundle.assetlayer.plugin.template.Templates',
    function () {
        _.templateSettings = {
            interpolate: /\{\{(.+?)\}\}/g
        };

        this.popupInfoTemplate = _.template('<div class="popupInfoBusStopsIcons">{{busStopsIcons}}</div>' +
            '<div class="popupInfoChangeDirection">' +
            '<div class="changeDirectionButton">{{changeDirectionButton}}</div>' +
            '</div>');
        this.busStopsPopupIcons = _.template('<img src="api/images/{{imageId}}">');
        this.removeAssetTemplate = _.template('<p>Anna viimeinen voimassaolopäivä</p><p><input id="removeAssetDateInput" class="featureAttributeDate" type="text" /></p>');

        this.roadStyles = new OpenLayers.StyleMap({
            "select": new OpenLayers.Style(null, {
                rules: [
                    new OpenLayers.Rule({
                        symbolizer: {
                            "Line": {
                                strokeWidth: 6,
                                strokeOpacity: 1,
                                strokeColor: "#5eaedf"
                            }
                        }
                    })
                ]
            }),
            "default": new OpenLayers.Style(null, {
                rules: [
                    new OpenLayers.Rule({
                        symbolizer: {
                            "Line": {
                                strokeWidth: 3,
                                strokeOpacity: 1,
                                strokeColor: "#a4a4a2"
                            }
                        }
                    })
                ]
            })
        });

    }
);